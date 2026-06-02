package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.memory.MemoryCaptureParser
import com.moonlib.cosmos.data.memory.MemoryContextFormatter
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 朋友圈独立 AI 智能回复盖楼引擎
 * 
 * 职责单一：负责朋友圈的 NPC 智能熟人评论决策、以及大模型 API 的安全通讯和 JSON 解析。
 */
object MomentEngine {

    /**
     * 异步触发朋友圈盖楼评论
     */
    fun triggerNpcRepliesAsync(context: Context, momentId: String, onComplete: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkAndGenerateNpcReplies(context, momentId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * 智能判定联系人，并生成朋友圈回复列表
     */
    suspend fun checkAndGenerateNpcReplies(context: Context, momentId: String): List<Moment> = withContext(Dispatchers.IO) {
        val momentRepo = MomentRepository(context)
        val targetMoment = momentRepo.getMoment(momentId) ?: return@withContext emptyList()

        // 1. 获取所有已在聊天 App 中初始化的联系人资料 (排除 user 自身)
        val profiles = momentRepo.getProfiles().filter { it.characterId != "user" }
        if (profiles.isEmpty()) return@withContext emptyList()

        val profileRepo = CharacterProfileRepository(context)
        val systemProfiles = profileRepo.getProfiles()

        // 2. 拼接当前朋友圈回复树的完整历史
        val threadHistory = mutableListOf<Moment>()
        var current: Moment? = targetMoment
        while (current != null) {
            threadHistory.add(0, current) // 最老的在前
            current = current.parentId?.let { momentRepo.getMoment(it) }
        }

        val threadTextBuilder = AiHistoryFormatter.formatTimeline(
            items = threadHistory,
            timestampOf = { it.timestamp },
            bodyOf = { m ->
            val authorProf = momentRepo.getProfile(m.authorId)
            val authorNickname = authorProf?.nickname ?: m.authorId
            val replyPart = if (m.replyToUsername != null) " 回复了 ${m.replyToUsername}" else ""
                "[$authorNickname$replyPart]: ${m.content}"
            }
        )

        val directUserReplyTarget = if (targetMoment.authorId == "user") {
            targetMoment.parentId?.let { momentRepo.getMoment(it) }
        } else {
            null
        }
        val isDirectReplyToNpc = directUserReplyTarget
            ?.let { parent -> profiles.any { it.characterId == parent.authorId } }
            ?: false
        val activeProfiles = if (isDirectReplyToNpc) {
            profiles.filter { it.characterId == directUserReplyTarget!!.authorId }
        } else {
            profiles
        }
        val allowedReplyCharacterIds = activeProfiles.map { it.characterId }.toSet()

        val replyCountRule = if (isDirectReplyToNpc) {
            "只生成 1 条回复。玩家刚刚主动回复了某个联系人的朋友圈动态/评论，这是点名互动，只能由被回复的角色本人接话，禁止其他联系人插话，禁止返回空 replies。"
        } else {
            "生成 0 到 3 条回复。联系人之间关系亲密或各有性格，会刷到这条朋友圈并决定是否盖楼互动。如果都不合适，也可以返回空 replies。"
        }

        val targetInteractionNote = if (isDirectReplyToNpc) {
            val targetAuthor = momentRepo.getProfile(directUserReplyTarget!!.authorId)
            "玩家正在回复 ${targetAuthor?.nickname ?: directUserReplyTarget.authorId}。本次只模拟这一个被回复者的自然接话，不要安排其他联系人参与。"
        } else {
            "当前是玩家普通发朋友圈动态或非直接 NPC 互动，请按拟真刷到概率决定是否有人评论。"
        }

        // 3. 构造候选联系人的详细性格设定；历史统一放入 historyText
        val charactersInfo = StringBuilder()
        val maxContextSize = AiSettingsRepository(context).getMaxContextSize()
        val candidateProfiles = mutableListOf<com.moonlib.cosmos.data.profile.CharacterProfile>()

        for (prof in activeProfiles) {
            val systemProf = systemProfiles.firstOrNull { it.id == prof.characterId } ?: continue
            candidateProfiles.add(systemProf)
            val promptProcessed = systemProf.prompt
                .replace("{{char}}", prof.nickname)
                .replace("{{user}}", "玩家")

            charactersInfo.append("联系人 ID (character_id): ${prof.characterId}\n")
            charactersInfo.append("动态昵称: ${prof.nickname}\n")
            charactersInfo.append("动态简介: ${prof.bio}\n")
            charactersInfo.append("【人设性格作息提示词】：\n$promptProcessed\n")
            charactersInfo.append("=========================================\n\n")
        }
        val recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
            context = context,
            charProfiles = candidateProfiles,
            maxContextSize = maxContextSize,
            playerName = "玩家"
        )
        val mergedHistoryText = AiHistoryFormatter.formatHistoryItems(recentMergedHistory)
        val memoryText = ConversationContextBuilder.buildMemoryListForCharacters(
            context = context,
            charProfiles = candidateProfiles
        )

        // 4. 获取系统提示词基底与 AI 设置
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()
        // 5. 发起 AI 请求并获取回复
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("未检测到激活的 AI 模型。请在【设置】中配置。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整。")
        }

        val responseText = try {
            AiRequestClient.execute(
                context = context,
                request = AiSceneRequest(
                    sceneType = AiSceneType.SOCIAL_REPLY_MOMENT,
                    systemPrompt = mainPrompt,
                    worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                    personaPrompt = "【已加好友的联系人列表及其设定】\n$charactersInfo",
                    outputRequirement = """
                        你正在模拟聊天 App 朋友圈的熟人评论。请根据角色性格、人设、关系与当前时间决定是否回复。
                        $replyCountRule 正文必须控制在 1 到 2 句话内，严禁 emoji、颜文字和动作描写，指代用户必须用“你”。
                        
                        ${MemoryContextFormatter.CAPTURE_REQUIREMENT}
                    """.trimIndent(),
                    jsonStructure = """{"replies":[{"character_id":"回复角色ID","reply_to_username":"被回复昵称","content":"评论内容","parent_id":"$momentId 或 reply_index_0","time_offset_seconds":20}],"memories":[]}""",
                    memoryText = memoryText,
                    historyText = """
                        【候选联系人统一宽历史】
                        $mergedHistoryText
                        
                        【当前朋友圈对话树历史】
                        $threadTextBuilder
                        
                        【当前触发场景】
                        $targetInteractionNote
                    """.trimIndent(),
                    userInput = "目标动态: ${targetMoment.content}",
                    logCharacterName = "朋友圈AI评论引擎",
                    logUserInput = "目标动态: ${targetMoment.content}",
                    responseSchema = AiJsonSchemaFactory.socialRepliesSchema("cosmos_moment_replies")
                )
            ).rawResponse
        } catch (e: Exception) {
            "AI_REQUEST_FAILED: ${e.message.orEmpty()}"
        }

        // 7. 解析返回的盖楼评论并保存
        val repliesArray = try {
            val cleanJson = AiResponseCleaner.cleanJson(responseText)
            val jsonObj = JSONObject(cleanJson)
            MemoryCaptureParser.captureFromResponse(
                context = context,
                jsonObj = jsonObj,
                sceneType = AiSceneType.SOCIAL_REPLY_MOMENT,
                fallbackCharacterIds = allowedReplyCharacterIds.toList(),
                validCharacterIds = systemProfiles.map { it.id }.toSet()
            )
            jsonObj.optJSONArray("replies") ?: JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }

        val savedReplies = mutableListOf<Moment>()
        val indexToIdMap = mutableMapOf<String, String>()

        var lastTime = targetMoment.timestamp

        for (i in 0 until repliesArray.length()) {
            val repObj = repliesArray.getJSONObject(i)
            val charId = repObj.optString("character_id", "")
            val content = repObj.optString("content", "")
            val replyToUser = if (repObj.isNull("reply_to_username")) null else repObj.getString("reply_to_username")
            val parentIdStr = repObj.optString("parent_id", momentId)
            val offsetSec = repObj.optInt("time_offset_seconds", 20)

            if (charId.isBlank() || content.isBlank()) continue
            if (charId !in allowedReplyCharacterIds) continue

            // 查找是否是联系人
            val authorProf = momentRepo.getProfile(charId) ?: continue

            val realParentId = if (parentIdStr.startsWith("reply_index_")) {
                indexToIdMap[parentIdStr] ?: momentId
            } else {
                momentId
            }

            val finalTime = lastTime + offsetSec * 1000L
            lastTime = finalTime

            val replyUuid = UUID.randomUUID().toString()
            indexToIdMap["reply_index_$i"] = replyUuid

            val newReply = Moment(
                id = replyUuid,
                authorId = charId,
                content = content,
                imagePath = null,
                videoPath = null,
                timestamp = finalTime,
                parentId = realParentId,
                replyToUsername = replyToUser
            )
            momentRepo.saveMoment(newReply)
            savedReplies.add(newReply)
        }

        // 8. 如果有回复，推进虚拟时间为最后一条回复的时间
        if (savedReplies.isNotEmpty()) {
            val maxTime = savedReplies.maxOf { it.timestamp }
            VirtualTimeManager.updateTime(maxTime)
        }

        savedReplies
    }

    // ── 内部辅助与请求函数 ──────────────────────────────────────────

}
