package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiAuthorizationHeader
import com.moonlib.cosmos.data.settings.AiChatCompletionResponseParser
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiReasoningRequestOptions
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.AiVertexConfig
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

        val replyCountRule = if (isDirectReplyToNpc) {
            "生成 1 到 3 条回复。玩家刚刚主动回复了联系人的朋友圈动态/评论，这是熟人间的直接社交互动，必须至少让被回复的角色或相关联系人给出一句自然的回应，禁止返回空 replies。"
        } else {
            "生成 0 to 3 条回复。联系人之间关系亲密或各有性格，会刷到这条朋友圈并决定是否盖楼互动。如果都不合适，也可以返回空 replies。"
        }

        val targetInteractionNote = if (isDirectReplyToNpc) {
            val targetAuthor = momentRepo.getProfile(directUserReplyTarget!!.authorId)
            "玩家正在回复 ${targetAuthor?.nickname ?: directUserReplyTarget.authorId}。优先让被回复者以熟人口吻接话，也可以让其他联系人插话。"
        } else {
            "当前是玩家普通发朋友圈动态或非直接 NPC 互动，请按拟真刷到概率决定是否有人评论。"
        }

        // 3. 构造候选联系人的详细性格设定与全局融合上下文
        val charactersInfo = StringBuilder()
        val maxContextSize = com.moonlib.cosmos.data.settings.AiSettingsRepository(context).getMaxContextSize().coerceAtMost(30)

        for (prof in profiles) {
            val systemProf = systemProfiles.firstOrNull { it.id == prof.characterId } ?: continue
            val promptProcessed = systemProf.prompt
                .replace("{{char}}", prof.nickname)
                .replace("{{user}}", "玩家")

            // 获取全局合并融合上下文
            val recentMerged = ConversationContextBuilder.buildForCharacter(context, systemProf, maxContextSize)
            val unifiedMemoryText = if (recentMerged.isEmpty()) {
                "（当前暂无与玩家的共同记忆与沟通历史）"
            } else {
                AiHistoryFormatter.formatTimeline(
                    items = recentMerged,
                    timestampOf = { it.timestamp },
                    bodyOf = { msg ->
                    val senderName = if (msg.senderId == "user") "玩家" else prof.nickname
                        "$senderName 的${msg.prefix}: ${msg.content}"
                    }
                )
            }

            charactersInfo.append("联系人 ID (character_id): ${prof.characterId}\n")
            charactersInfo.append("动态昵称: ${prof.nickname}\n")
            charactersInfo.append("动态简介: ${prof.bio}\n")
            charactersInfo.append("【人设性格作息提示词】：\n$promptProcessed\n")
            charactersInfo.append("【该联系人拥有的最新通用融合记忆（含私聊、互动、日记、推特及动态）】：\n$unifiedMemoryText\n")
            charactersInfo.append("=========================================\n\n")
        }

        // 4. 获取系统提示词基底与 AI 设置
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 5. 组装专为独立朋友圈设计的 System Prompt
        val systemPrompt = """
            $mainPrompt

            【CosmOS 虚拟手机聊天 App 内的朋友圈动态仿真盖楼评论系统】
            你现在扮演的是 CosmOS 系统内“聊天 App 朋友圈动态的仿真 AI 评论引擎”。
            玩家和联系人们在聊天 App 的“动态（Moment）”中发帖，你需要判定哪些联系人会刷到并回复。
            **这是完全独立于【推特 (Twitter)】的另一个熟人朋友圈社交 App！** 这里的互动属于更私密、亲密的熟人圈社交，谈话氛围更接地气、偏向日常生活的互动（如：调侃、互怼、关切、点赞式的评论），严禁使用那种公开推特大 V 式的网感宣传口吻。

            【当前虚拟世界的时间】：$currentVirtualTimeStr

            【已加好友的联系人列表及其设定】：
            -----------------------------------------
            $charactersInfo
            -----------------------------------------

            【当前推文对话树历史（升序）】：
            -----------------------------------------
            $threadTextBuilder
            -----------------------------------------

            【当前触发场景】：
            $targetInteractionNote

            【评论决策与要求】：
            1. **熟人社交口吻**：根据每个联系人性格、人设以及与玩家、其他 NPC 的私密关系进行回复决策。$replyCountRule
            2. **文字规范**：消息正文必须控制在 1 到 2 句话内（30字以内），严禁任何 emoji、颜文字、或动作描写！指代用户必须用第二人称“你”，绝对禁止使用“他”或“她”！
            3. **层级关系**：回复的 `parent_id` 可以是当前叶子结点的动态 ID （即 `"$momentId"`），也可以是本组回复中前面某条评论的临时 ID（例如: `reply_index_0`），代表联系人互相评论对方的评论，形成有趣的“套娃盖楼”。
            4. **时间偏移**：每条回复指定一个 `time_offset_seconds`（10 到 120 秒之间，逐渐递增），用来代表真实用户刷手机、打字发送的时间间隔。

            【底层通信输出格式】：
            为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "replies": [
                {
                  "character_id": "回复角色的 character_id",
                  "reply_to_username": "正在回复的那个人的昵称（例如：爱丽丝）",
                  "content": "这照片真不错，求定位！",
                  "parent_id": "直接被回复的动态ID（原贴填 $momentId，若回复本组里另一个角色的评论，可填其在 replies 中的 index，例如: 'reply_index_0'）",
                  "time_offset_seconds": 20
                }
              ]
            }
        """.trimIndent()

        // 6. 发起 AI 请求并获取回复
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
                    personaPrompt = "【已加好友的联系人列表及其设定】\n$charactersInfo",
                    outputRequirement = """
                        你正在模拟聊天 App 朋友圈的熟人评论。请根据角色性格、人设、关系与当前时间决定是否回复。
                        $replyCountRule 正文必须控制在 1 到 2 句话内，严禁 emoji、颜文字和动作描写，指代用户必须用“你”。
                    """.trimIndent(),
                    jsonStructure = """{"replies":[{"character_id":"回复角色ID","reply_to_username":"被回复昵称","content":"评论内容","parent_id":"$momentId 或 reply_index_0","time_offset_seconds":20}]}""",
                    historyText = "【当前朋友圈对话树历史】\n$threadTextBuilder\n\n【当前触发场景】\n$targetInteractionNote",
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
