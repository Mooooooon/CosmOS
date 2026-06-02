package com.moonlib.cosmos.data.twitter

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
 * 推特 AI 回复与事件模拟引擎
 * 
 * 职责单一：负责推特的 NPC 智能盖楼回复决策、虚拟时间推演主动发推逻辑，以及底层的 AI 数据交互与 JSON 解析。
 */
object TwitterEngine {

    /**
     * 当用户发推或回复后，异步触发被关注的角色进行盖楼评论
     */
    fun triggerNpcRepliesAsync(context: Context, tweetId: String, onComplete: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkAndGenerateNpcReplies(context, tweetId)
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
     * 智能判定已关注角色，并生成针对某条推文的盖楼回复列表
     */
    suspend fun checkAndGenerateNpcReplies(context: Context, tweetId: String): List<Tweet> = withContext(Dispatchers.IO) {
        val twitterRepo = TwitterRepository(context)
        val targetTweet = twitterRepo.getTweet(tweetId) ?: return@withContext emptyList()
        
        // 1. 获取所有已关注的角色推特档案
        val followedProfiles = twitterRepo.getFollowedProfiles()
        if (followedProfiles.isEmpty()) return@withContext emptyList()

        val profileRepo = CharacterProfileRepository(context)
        val systemProfiles = profileRepo.getProfiles()
        
        // 2. 拼接当前推文回复树的完整历史
        val threadHistory = mutableListOf<Tweet>()
        var current: Tweet? = targetTweet
        while (current != null) {
            threadHistory.add(0, current) // 最老的在前
            current = current.parentId?.let { twitterRepo.getTweet(it) }
        }

        val threadTextBuilder = AiHistoryFormatter.formatTimeline(
            items = threadHistory,
            timestampOf = { it.timestamp },
            bodyOf = { t ->
            val authorProf = twitterRepo.getProfile(t.authorId)
            val authorName = authorProf?.nickname ?: t.authorId
            val authorHandle = authorProf?.username ?: t.authorId
            val replyPart = if (t.replyToUsername != null) " 回复 @${t.replyToUsername}" else ""
                "[@${authorHandle} ($authorName)$replyPart]: ${t.content}"
            }
        )
        val directUserReplyTarget = if (targetTweet.authorId == "user") {
            targetTweet.parentId?.let { twitterRepo.getTweet(it) }
        } else {
            null
        }
        val isDirectReplyToFollowedCharacter = directUserReplyTarget
            ?.let { parent -> followedProfiles.any { it.characterId == parent.authorId } }
            ?: false
        val activeProfiles = if (isDirectReplyToFollowedCharacter) {
            followedProfiles.filter { it.characterId == directUserReplyTarget!!.authorId }
        } else {
            followedProfiles
        }
        val allowedReplyCharacterIds = activeProfiles.map { it.characterId }.toSet()
        val replyCountRule = if (isDirectReplyToFollowedCharacter) {
            "只生成 1 条回复。玩家刚刚主动回复了某个已关注角色的推文/评论，这属于点名互动，只能由被回复的角色本人接话，禁止其他已关注角色插话，禁止返回空 replies。"
        } else {
            "生成 0 到 3 条回复。有些角色性格热情，可能立刻评论；有的角色则可能互怼；如果确实没人合适，也可以返回空 replies。"
        }
        val targetInteractionNote = if (isDirectReplyToFollowedCharacter) {
            val targetAuthor = twitterRepo.getProfile(directUserReplyTarget!!.authorId)
            "玩家正在回复 @${targetAuthor?.username ?: directUserReplyTarget.authorId}（${targetAuthor?.nickname ?: directUserReplyTarget.authorId}）。本次只模拟这一个被回复者的自然接话，不要安排其他已关注角色参与。"
        } else {
            "当前是普通发推或非直接 NPC 互动，请按拟真刷到概率决定是否有人回复。"
        }

        // 3. 构造候选角色的详细性格作息设定；历史统一放入 historyText
        val charactersInfo = StringBuilder()
        val maxContextSize = AiSettingsRepository(context).getMaxContextSize()
        val candidateProfiles = mutableListOf<com.moonlib.cosmos.data.profile.CharacterProfile>()
        
        for (fProf in activeProfiles) {
            val systemProf = systemProfiles.firstOrNull { it.id == fProf.characterId } ?: continue
            candidateProfiles.add(systemProf)
            val promptProcessed = systemProf.prompt
                .replace("{{char}}", fProf.nickname)
                .replace("{{user}}", "玩家")

            charactersInfo.append("角色 ID (character_id): ${fProf.characterId}\n")
            charactersInfo.append("推特名字: ${fProf.nickname}\n")
            charactersInfo.append("推特用户名: @${fProf.username}\n")
            charactersInfo.append("个人简介: ${fProf.bio}\n")
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

        // 4. 获取全局系统提示词基底与 AI 设置
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
                    sceneType = AiSceneType.SOCIAL_REPLY_TWITTER,
                    systemPrompt = mainPrompt,
                    worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                    personaPrompt = "【已关注的候选角色列表及其人设设定】\n$charactersInfo",
                    outputRequirement = """
                        你正在模拟 CosmOS 虚拟推特的评论盖楼。请根据角色性格、作息、关系与当前时间决定是否回复。
                        $replyCountRule 正文必须控制在 1 到 2 句话内，严禁 emoji、颜文字和动作描写，指代用户必须用“你”。
                        
                        ${MemoryContextFormatter.CAPTURE_REQUIREMENT}
                    """.trimIndent(),
                    jsonStructure = """{"replies":[{"character_id":"回复角色ID","reply_to_username":"被回复用户名","content":"评论内容","parent_id":"$tweetId 或 reply_index_0","time_offset_seconds":15}],"memories":[]}""",
                    memoryText = memoryText,
                    historyText = """
                        【候选角色统一宽历史】
                        $mergedHistoryText
                        
                        【当前推文对话树历史】
                        $threadTextBuilder
                        
                        【当前触发场景】
                        $targetInteractionNote
                    """.trimIndent(),
                    userInput = "目标推文: ${targetTweet.content}",
                    logCharacterName = "推特AI评论引擎",
                    logUserInput = "目标推文: ${targetTweet.content}",
                    responseSchema = AiJsonSchemaFactory.socialRepliesSchema("cosmos_twitter_replies")
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
                sceneType = AiSceneType.SOCIAL_REPLY_TWITTER,
                fallbackCharacterIds = allowedReplyCharacterIds.toList(),
                validCharacterIds = systemProfiles.map { it.id }.toSet()
            )
            jsonObj.optJSONArray("replies") ?: JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }
        
        val savedReplies = mutableListOf<Tweet>()
        val indexToIdMap = mutableMapOf<String, String>() // 用于映射 'reply_index_X' 到真正的 UUID

        var lastTime = targetTweet.timestamp

        for (i in 0 until repliesArray.length()) {
            val repObj = repliesArray.getJSONObject(i)
            val charId = repObj.optString("character_id", "")
            val content = repObj.optString("content", "")
            val replyToUser = if (repObj.isNull("reply_to_username")) null else repObj.getString("reply_to_username")
            val parentIdStr = repObj.optString("parent_id", tweetId)
            val offsetSec = repObj.optInt("time_offset_seconds", 15)

            if (charId.isBlank() || content.isBlank()) continue
            if (charId !in allowedReplyCharacterIds) continue

            // 查找是否是已关注的角色
            val authorProf = twitterRepo.getProfile(charId) ?: continue

            val realParentId = if (parentIdStr.startsWith("reply_index_")) {
                indexToIdMap[parentIdStr] ?: tweetId
            } else {
                tweetId
            }

            val finalTime = lastTime + offsetSec * 1000L
            lastTime = finalTime

            val replyUuid = UUID.randomUUID().toString()
            indexToIdMap["reply_index_$i"] = replyUuid

            val newReply = Tweet(
                id = replyUuid,
                authorId = charId,
                content = content,
                imagePath = null,
                timestamp = finalTime,
                parentId = realParentId,
                replyToUsername = replyToUser
            )
            twitterRepo.saveTweet(newReply)
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
