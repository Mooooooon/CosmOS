package com.moonlib.cosmos.data.twitter

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
        val replyCountRule = if (isDirectReplyToFollowedCharacter) {
            "生成 1 到 3 条回复。玩家刚刚主动回复了已关注角色的推文/评论，这属于直接社交互动，必须至少让被回复的角色或相关角色给出一句自然回应，禁止返回空 replies。"
        } else {
            "生成 0 到 3 条回复。有些角色性格热情，可能立刻评论；有的角色则可能互怼；如果确实没人合适，也可以返回空 replies。"
        }
        val targetInteractionNote = if (isDirectReplyToFollowedCharacter) {
            val targetAuthor = twitterRepo.getProfile(directUserReplyTarget!!.authorId)
            "玩家正在回复 @${targetAuthor?.username ?: directUserReplyTarget.authorId}（${targetAuthor?.nickname ?: directUserReplyTarget.authorId}）。优先让被回复者按人设接话，也可以让其他已关注角色插一句。"
        } else {
            "当前是普通发推或非直接 NPC 互动，请按拟真刷到概率决定是否有人回复。"
        }

        // 3. 构造候选角色的详细性格作息设定与通用上下文（线上聊天/实体互动/日记/推特合并的全局记忆）
        val charactersInfo = StringBuilder()
        val maxContextSize = com.moonlib.cosmos.data.settings.AiSettingsRepository(context).getMaxContextSize().coerceAtMost(30)
        
        for (fProf in followedProfiles) {
            val systemProf = systemProfiles.firstOrNull { it.id == fProf.characterId } ?: continue
            val promptProcessed = systemProf.prompt
                .replace("{{char}}", fProf.nickname)
                .replace("{{user}}", "玩家")
                
            // 获取该 NPC 的全局合并通用上下文记忆
            val recentMerged = ConversationContextBuilder.buildForCharacter(context, systemProf, maxContextSize)
            val unifiedMemoryText = if (recentMerged.isEmpty()) {
                "（当前暂无与玩家的共同记忆与沟通历史）"
            } else {
                AiHistoryFormatter.formatTimeline(
                    items = recentMerged,
                    timestampOf = { it.timestamp },
                    bodyOf = { msg ->
                    val senderName = if (msg.senderId == "user") "玩家" else fProf.nickname
                        "$senderName 的${msg.prefix}: ${msg.content}"
                    }
                )
            }

            charactersInfo.append("角色 ID (character_id): ${fProf.characterId}\n")
            charactersInfo.append("推特名字: ${fProf.nickname}\n")
            charactersInfo.append("推特用户名: @${fProf.username}\n")
            charactersInfo.append("个人简介: ${fProf.bio}\n")
            charactersInfo.append("【人设性格作息提示词】：\n$promptProcessed\n")
            charactersInfo.append("【该角色拥有的最新通用融合记忆（含线上私聊、线下互动、日记及推特动态）】：\n$unifiedMemoryText\n")
            charactersInfo.append("=========================================\n\n")
        }

        // 4. 获取全局系统提示词基底与 AI 设置
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 5. 组装专为推特互动的 System Prompt
        val systemPrompt = """
            $mainPrompt
            
            【CosmOS 虚拟手机推特（Twitter）盖楼评论系统】
            你现在正扮演 CosmOS 系统的“推特 AI 仿真互动引擎”。
            当玩家发送新推文，或者在已有评论区中发表回复后，你需要根据被关注角色的性格特征、彼此关系以及当时的时间，判定哪些人会刷到这条推文并会回复它。她们不仅可以回复原推文，还可能针对彼此的回复进行有趣的“盖楼套娃式互动”。
            
            【当前虚拟世界的时间】：$currentVirtualTimeStr
            
            【已关注的候选角色列表及其人设设定】：
            -----------------------------------------
            $charactersInfo
            -----------------------------------------
            
            【当前推文对话树历史（升序）】：
            -----------------------------------------
            $threadTextBuilder
            -----------------------------------------

            【当前触发场景】：
            $targetInteractionNote
            
            【盖楼决策与回复要求（极其重要）】：
            1. **拟真盖楼互动**：根据角色性格、作息以及彼此的关系，$replyCountRule
            2. **文字规范**：消息内容必须控制在 1 到 2 句话内（30字以内），严禁任何 emoji、颜文字或小括号内的动作描写！指代用户必须用第二人称“你”，绝对禁止使用“他”或“她”！
            3. **层级关系**：回复的 `parent_id` 可以是当前叶子结点推文的 ID （即 `"$tweetId"`），也可以是本组回复中前面那条回复的临时 ID，从而实现“角色互相回复彼此的评论”。
            4. **时间偏移**：每条回复指定一个 `time_offset_seconds`（在 10 到 120 秒之间，逐渐递增），用来代表真实用户刷推特、打字和发送的时间间隔。
            
            【底层通信输出格式】：
            为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "replies": [
                {
                  "character_id": "回复角色的 character_id",
                  "reply_to_username": "正在回复的那个人的推特用户名（不含@，例如 alice_wonderland）",
                  "content": "这景色真美，我也想去！",
                  "parent_id": "直接被回复的推文ID（原贴填 $tweetId，如果回复本组里另一个角色的评论，可填其在 replies 中的 index，例如: 'reply_index_0'）",
                  "time_offset_seconds": 15
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
                    sceneType = AiSceneType.SOCIAL_REPLY_TWITTER,
                    systemPrompt = mainPrompt,
                    personaPrompt = "【已关注的候选角色列表及其人设设定】\n$charactersInfo",
                    outputRequirement = """
                        你正在模拟 CosmOS 虚拟推特的评论盖楼。请根据角色性格、作息、关系与当前时间决定是否回复。
                        $replyCountRule 正文必须控制在 1 到 2 句话内，严禁 emoji、颜文字和动作描写，指代用户必须用“你”。
                    """.trimIndent(),
                    jsonStructure = """{"replies":[{"character_id":"回复角色ID","reply_to_username":"被回复用户名","content":"评论内容","parent_id":"$tweetId 或 reply_index_0","time_offset_seconds":15}]}""",
                    historyText = "【当前推文对话树历史】\n$threadTextBuilder\n\n【当前触发场景】\n$targetInteractionNote",
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
