package com.moonlib.cosmos.data.time

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.chat.Moment
import com.moonlib.cosmos.data.chat.MomentRepository
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.memory.MemoryCaptureParser
import com.moonlib.cosmos.data.memory.MemoryContextFormatter
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.twitter.Tweet
import com.moonlib.cosmos.data.twitter.TwitterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 时间跳过线上行为模拟引擎。
 *
 * 职责单一：在时间跳过期间，用一次 AI 请求统一模拟私聊消息、朋友圈动态与推特动态。
 */
object TimeSkipEngine {

    data class TimeSkipResult(
        val success: Boolean,
        val simulatedMessageCount: Int,
        val simulatedMomentCount: Int = 0,
        val simulatedTweetCount: Int = 0,
        val errorMessage: String? = null
    )

    private data class OnlineCandidate(
        val characterId: String,
        val profile: CharacterProfile,
        val chatContactId: String? = null,
        val chatNickname: String? = null,
        val canSendMessage: Boolean = false,
        val canPostMoment: Boolean = false,
        val canPostTweet: Boolean = false,
        val momentNickname: String? = null,
        val twitterNickname: String? = null,
        val twitterUsername: String? = null,
        val twitterBio: String? = null
    )

    suspend fun executeTimeSkip(
        context: Context,
        startTimeMillis: Long,
        endTimeMillis: Long,
        userActivity: String
    ): TimeSkipResult = withContext(Dispatchers.IO) {
        try {
            val chatRepo = ChatRepository(context)
            val momentRepo = MomentRepository(context)
            val twitterRepo = TwitterRepository(context)
            val profileRepo = CharacterProfileRepository(context)
            val aiSettingsRepo = AiSettingsRepository(context)
            val allProfiles = profileRepo.getProfiles()
            val playerProfile = allProfiles.firstOrNull { it.isPlayer }
            val playerRealName = playerProfile?.name ?: chatRepo.getUserNickname()
            val candidates = buildOnlineCandidates(context, allProfiles)

            if (candidates.isEmpty()) {
                saveTimeSkipHistory(context, startTimeMillis, endTimeMillis, userActivity)
                VirtualTimeManager.updateTime(endTimeMillis)
                return@withContext TimeSkipResult(success = true, simulatedMessageCount = 0)
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
            val startTimeStr = sdf.format(Date(startTimeMillis))
            val endTimeStr = sdf.format(Date(endTimeMillis))
            val maxContextSize = aiSettingsRepo.getMaxContextSize()
            val candidateProfiles = candidates.map { it.profile }.distinctBy { it.id }
            val recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
                context = context,
                charProfiles = candidateProfiles,
                maxContextSize = maxContextSize,
                playerName = playerRealName
            )
            val mergedHistoryText = AiHistoryFormatter.formatHistoryItems(recentMergedHistory)
            val memoryText = ConversationContextBuilder.buildMemoryListForCharacters(
                context = context,
                charProfiles = candidateProfiles
            )

            val personaPrompt = candidates.joinToString("\n\n") { candidate ->
                val processedPrompt = candidate.profile.prompt
                    .replace("{{char}}", candidate.profile.name)
                    .replace("{{user}}", playerRealName)
                val abilities = buildList {
                    if (candidate.canSendMessage) add("私聊消息")
                    if (candidate.canPostMoment) add("朋友圈动态")
                    if (candidate.canPostTweet) add("推特动态")
                }.joinToString("、")
                """
                角色 ID: ${candidate.characterId}
                角色名字: ${candidate.profile.name}
                可模拟平台: $abilities
                聊天昵称: ${candidate.chatNickname ?: "未开通"}
                朋友圈昵称: ${candidate.momentNickname ?: "未开通"}
                推特昵称/用户名: ${candidate.twitterNickname ?: "未开通"}${candidate.twitterUsername?.let { " / @$it" } ?: ""}${if (candidate.twitterBio != null) "\n                推特账号简介: ${candidate.twitterBio}" else ""}
                【人设与作息】
                $processedPrompt
                """.trimIndent()
            }

            val outputRequirement = """
                你现在是 CosmOS 时间跳过期间的统一线上行为调度器。用户在这段时间内处于离线状态，你需要一次性判断各角色是否会发生线上行为。
                私聊消息、朋友圈动态、推特动态必须在同一个 JSON 中统一输出，不要把推特或朋友圈拆成额外请求。
                所有 time 必须严格落在 [$startTimeStr, $endTimeStr] 内。

                【私聊消息生成规则（重要）】
                每个角色在整段时间跳过内只能主动开口一次。所谓"一次开口"是指：角色因为某个原因主动联系玩家，把想说的话一次性发出（类似现实中把一段话分成 2-3 条短消息发送），然后等待玩家回复。
                - 严禁模拟"角色发消息 → 等玩家回复 → 角色再追发"的多轮自言自语场景，玩家不在线不可能回复。
                - 一次开口的若干条消息 time 必须集中在很短的时间段内（几分钟以内），内容是同一个话题的自然延伸短句，不能出现话题跳转或等待回应后的追问。
                - 如果角色性格不主动或近期已有大量对话，可以选择不发消息（不在 simulated_messages 中输出该角色）。
                - 内容必须契合该角色的作息与当前状态：上班/上课时间不应约饭邀玩，深夜不应发工作相关通知，凌晨熟睡状态不应有任何消息。
                - 发消息的动机必须合理：有新鲜事想分享、想起某件事要说、有事找玩家……不能无缘无故发"在吗"或刷存在感。
                - 内容口语化、自然短句，契合两人之间的亲密关系程度。

                【朋友圈动态生成规则（重要）】
                朋友圈逻辑类似微信朋友圈、QQ空间——这是熟人社交圈，内容是私人生活的分享。
                - 内容偏向：日常生活点滴（吃了什么、去了哪里、拍到的风景）、情绪感悟、朋友聚会、宠物、家人等私生活内容。
                - 不适合发：工作汇报、公开宣言、向陌生人喊话类内容。
                - 配图应真实自然：自拍、美食照、风景照、出行记录等，不要太专业或广告感。
                - 语气轻松随意，像和朋友分享生活，可以带有个人情绪和细节，字数不必太多。
                - 时间要符合作息：凌晨不发，工作繁忙时段少发，下班傍晚、休息日更活跃。
                每个角色最多 1 条朋友圈动态，如无合适内容可不发。

                【推特动态生成规则（重要）】
                推特逻辑类似微博、小红书、Twitter——这是面向公开互联网用户的发言平台。
                - 【核心要求】每个角色的"推特账号简介"定义了该账号的定位与核心话题圈，生成的推特内容必须与简介高度吻合。例如简介中提到"摄影"就围绕摄影类内容发布，提到"FGO"就围绕游戏相关内容发布，切忌输出与简介无关的泛泛生活感悟。
                - 内容偏向：观点输出、与简介定位匹配的兴趣爱好分享、时事评论、生活感悟的公开版、对某个话题的想法等；比朋友圈更"对外"。
                - 适合发：与简介定位匹配的有趣见闻和感想、某件事的个人看法、推荐内容、分享简介中提及的爱好相关内容。
                - 不适合发：与简介定位完全无关的内容、过于私密的个人情感（那应发朋友圈）、指向特定人（如玩家）的私人对话。
                - 配图可以是截图、拍摄的场景、自制图表、二次元图等，比朋友圈更多元；配图主题也应与简介定位一致。
                - 语气可以更公开化，有观点有态度，但需符合角色人设风格与简介中展示的账号气质。
                每个角色最多 1 条推特动态，如无合适内容可不发。

                【通用规范】
                所有文字严禁 emoji、颜文字和动作描写，指代玩家必须使用第二人称"你"。
                
                ${MemoryContextFormatter.CAPTURE_REQUIREMENT}
            """.trimIndent()

            val jsonStructure = """
                {
                  "simulated_messages": [
                    {"character_id":"角色ID","type":"text","time":"yyyy-MM-dd HH:mm:ss","content":"消息正文","extra":"可选"}
                  ],
                  "simulated_moments": [
                    {"character_id":"角色ID","content":"朋友圈正文","has_image":false,"image_description":"如果 has_image=true，必填写一段生动具体的图片画面描述（20-50字）让人能在脑中清晰还原这张图片","has_video":false,"video_description":"如果 has_video=true，必填写一段生动具体的视频动态画面描述（20-50字）让人能感受到现场感","time":"yyyy-MM-dd HH:mm:ss"}
                  ],
                  "simulated_tweets": [
                    {"character_id":"角色ID","content":"推特正文","has_image":false,"image_description":"如果 has_image=true，必填写一段生动具体的图片画面描述（20-50字）让人能在脑中清晰还原这张图片","time":"yyyy-MM-dd HH:mm:ss"}
                  ],
                  "memories": []
                }
            """.trimIndent()

            val userInput = """
                起始虚拟时间: $startTimeStr
                结束虚拟时间: $endTimeStr
                玩家真实姓名: $playerRealName
                玩家在这段时间内正在做什么: ${if (userActivity.isBlank()) "日常活动/未明确说明" else userActivity}
            """.trimIndent()

            val result = AiRequestClient.execute(
                context = context,
                request = AiSceneRequest(
                    sceneType = AiSceneType.TIME_SKIP_ONLINE,
                    systemPrompt = SystemPromptRepository(context).getMainPromptContent(),
                    worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                    personaPrompt = personaPrompt,
                    outputRequirement = outputRequirement,
                    jsonStructure = jsonStructure,
                    memoryText = memoryText,
                    historyText = """
                        【候选角色统一宽历史】
                        $mergedHistoryText
                    """.trimIndent(),
                    userInput = userInput,
                    logCharacterName = "时间跳过线上行为模拟器",
                    logUserInput = "跳过区间: $startTimeStr -> $endTimeStr, 用户活动: ${if (userActivity.isBlank()) "无" else userActivity}",
                    responseSchema = AiJsonSchemaFactory.timeSkipOnlineSchema()
                )
            )

            val jsonObj = JSONObject(AiResponseCleaner.cleanJson(result.rawResponse))
            MemoryCaptureParser.captureFromResponse(
                context = context,
                jsonObj = jsonObj,
                sceneType = AiSceneType.TIME_SKIP_ONLINE,
                fallbackCharacterIds = candidateProfiles.map { it.id },
                validCharacterIds = allProfiles.map { it.id }.toSet()
            )
            val candidateMap = candidates.associateBy { it.characterId }
            val messageCount = saveSimulatedMessages(
                jsonArray = jsonObj.optJSONArray("simulated_messages") ?: JSONArray(),
                candidates = candidateMap,
                chatRepo = chatRepo,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
            )
            val momentCount = saveSimulatedMoments(
                jsonArray = jsonObj.optJSONArray("simulated_moments") ?: JSONArray(),
                candidates = candidateMap,
                momentRepo = momentRepo,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis
            )
            val tweetCount = saveSimulatedTweets(
                jsonArray = jsonObj.optJSONArray("simulated_tweets") ?: JSONArray(),
                candidates = candidateMap,
                twitterRepo = twitterRepo,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis
            )

            saveTimeSkipHistory(context, startTimeMillis, endTimeMillis, userActivity)
            VirtualTimeManager.updateTime(endTimeMillis)
            TimeSkipResult(
                success = true,
                simulatedMessageCount = messageCount,
                simulatedMomentCount = momentCount,
                simulatedTweetCount = tweetCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            TimeSkipResult(false, 0, 0, 0, e.message ?: "时间跳过线上行为模拟失败")
        }
    }

    private fun saveTimeSkipHistory(
        context: Context,
        startTimeMillis: Long,
        endTimeMillis: Long,
        userActivity: String
    ) {
        TimeSkipHistoryRepository(context).addEntry(
            TimeSkipHistoryEntry(
                id = UUID.randomUUID().toString(),
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                userActivity = userActivity.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun buildOnlineCandidates(
        context: Context,
        allProfiles: List<CharacterProfile>
    ): List<OnlineCandidate> {
        val chatRepo = ChatRepository(context)
        val momentRepo = MomentRepository(context)
        val twitterRepo = TwitterRepository(context)
        val byId = linkedMapOf<String, OnlineCandidate>()

        for (contact in chatRepo.getContacts()) {
            val profile = allProfiles.firstOrNull { it.id == contact.characterId && !it.isPlayer } ?: continue
            val existing = byId[profile.id]
            byId[profile.id] = (existing ?: OnlineCandidate(profile.id, profile)).copy(
                chatContactId = contact.id,
                chatNickname = contact.nickname,
                canSendMessage = true,
                canPostMoment = true
            )
        }

        for (momentProfile in momentRepo.getProfiles().filter { it.characterId != "user" }) {
            val profile = allProfiles.firstOrNull { it.id == momentProfile.characterId && !it.isPlayer } ?: continue
            val existing = byId[profile.id]
            byId[profile.id] = (existing ?: OnlineCandidate(profile.id, profile)).copy(
                canPostMoment = true,
                momentNickname = momentProfile.nickname
            )
        }

        for (twitterProfile in twitterRepo.getFollowedProfiles()) {
            val profile = allProfiles.firstOrNull { it.id == twitterProfile.characterId && !it.isPlayer } ?: continue
            val existing = byId[profile.id]
            byId[profile.id] = (existing ?: OnlineCandidate(profile.id, profile)).copy(
                canPostTweet = true,
                twitterNickname = twitterProfile.nickname,
                twitterUsername = twitterProfile.username,
                twitterBio = twitterProfile.bio.takeIf { it.isNotBlank() }
            )
        }

        return byId.values.toList()
    }

    private fun saveSimulatedMessages(
        jsonArray: JSONArray,
        candidates: Map<String, OnlineCandidate>,
        chatRepo: ChatRepository,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Int {
        var savedCount = 0
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val characterId = obj.optString("character_id")
            val candidate = candidates[characterId] ?: continue
            val contactId = candidate.chatContactId ?: continue
            if (!candidate.canSendMessage) continue
            val content = obj.optString("content", "")
            val timeStr = obj.optString("time", "")
            if (content.isBlank() || timeStr.isBlank()) continue
            chatRepo.saveMessage(
                contactId,
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = contactId,
                    content = content,
                    timestamp = parseBoundedTime(timeStr, startTimeMillis, endTimeMillis),
                    type = obj.optString("type", "text"),
                    extra = if (obj.has("extra") && !obj.isNull("extra")) obj.optString("extra") else null
                )
            )
            savedCount++
        }
        return savedCount
    }

    private fun saveSimulatedMoments(
        jsonArray: JSONArray,
        candidates: Map<String, OnlineCandidate>,
        momentRepo: MomentRepository,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Int {
        val posted = mutableSetOf<String>()
        var savedCount = 0
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val characterId = obj.optString("character_id")
            val candidate = candidates[characterId] ?: continue
            if (!candidate.canPostMoment || !posted.add(characterId)) continue
            val content = obj.optString("content", "")
            val timeStr = obj.optString("time", "")
            if (content.isBlank() || timeStr.isBlank()) continue
            val imagePath = if (obj.optBoolean("has_image", false)) {
                obj.optString("image_description", "").takeIf { it.isNotBlank() }?.let { "simulated_image:$it" }
            } else {
                null
            }
            val videoPath = if (obj.optBoolean("has_video", false)) {
                obj.optString("video_description", "").takeIf { it.isNotBlank() }?.let { "simulated_video:$it" }
            } else {
                null
            }
            momentRepo.saveMoment(
                Moment(
                    id = UUID.randomUUID().toString(),
                    authorId = characterId,
                    content = content,
                    imagePath = imagePath,
                    videoPath = videoPath,
                    timestamp = parseBoundedTime(timeStr, startTimeMillis, endTimeMillis),
                    parentId = null
                )
            )
            savedCount++
        }
        return savedCount
    }

    private fun saveSimulatedTweets(
        jsonArray: JSONArray,
        candidates: Map<String, OnlineCandidate>,
        twitterRepo: TwitterRepository,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Int {
        val posted = mutableSetOf<String>()
        var savedCount = 0
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val characterId = obj.optString("character_id")
            val candidate = candidates[characterId] ?: continue
            if (!candidate.canPostTweet || !posted.add(characterId)) continue
            val content = obj.optString("content", "")
            val timeStr = obj.optString("time", "")
            if (content.isBlank() || timeStr.isBlank()) continue
            val imagePath = if (obj.optBoolean("has_image", false)) {
                obj.optString("image_description", "").takeIf { it.isNotBlank() }?.let { "simulated_image:$it" }
            } else {
                null
            }
            twitterRepo.saveTweet(
                Tweet(
                    id = UUID.randomUUID().toString(),
                    authorId = characterId,
                    content = content,
                    imagePath = imagePath,
                    timestamp = parseBoundedTime(timeStr, startTimeMillis, endTimeMillis),
                    parentId = null
                )
            )
            savedCount++
        }
        return savedCount
    }

    private fun parseBoundedTime(timeStr: String, startTimeMillis: Long, endTimeMillis: Long): Long {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fallback = startTimeMillis + (endTimeMillis - startTimeMillis) / 2
        return try {
            (parser.parse(timeStr)?.time ?: fallback).coerceIn(startTimeMillis, endTimeMillis)
        } catch (e: Exception) {
            fallback.coerceIn(startTimeMillis, endTimeMillis)
        }
    }
}
