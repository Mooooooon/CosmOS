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
        val twitterUsername: String? = null
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
                VirtualTimeManager.updateTime(endTimeMillis)
                return@withContext TimeSkipResult(success = true, simulatedMessageCount = 0)
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
            val startTimeStr = sdf.format(Date(startTimeMillis))
            val endTimeStr = sdf.format(Date(endTimeMillis))
            val maxMessages = aiSettingsRepo.getTimeSkipMaxMessages()
            val maxContextSize = aiSettingsRepo.getMaxContextSize()
            val candidateProfiles = candidates.map { it.profile }.distinctBy { it.id }
            val recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
                context = context,
                charProfiles = candidateProfiles,
                maxContextSize = maxContextSize,
                playerName = playerRealName
            )
            val mergedHistoryText = AiHistoryFormatter.formatHistoryItems(recentMergedHistory)

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
                推特昵称/用户名: ${candidate.twitterNickname ?: "未开通"}${candidate.twitterUsername?.let { " / @$it" } ?: ""}
                【人设与作息】
                $processedPrompt
                """.trimIndent()
            }

            val outputRequirement = """
                你现在是 CosmOS 时间跳过期间的统一线上行为调度器。用户在这段时间内处于离线状态，你需要一次性判断各角色是否会发生线上行为。
                私聊消息、朋友圈动态、推特动态必须在同一个 JSON 中统一输出，不要把推特或朋友圈拆成额外请求。
                每个角色最多生成 $maxMessages 条私聊消息，最多 1 条朋友圈动态，最多 1 条推特动态。
                所有 time 必须严格落在 [$startTimeStr, $endTimeStr] 内。
                私聊消息要像真实聊天，简洁口语化；朋友圈更私密日常；推特更公开化。所有文字严禁 emoji、颜文字和动作描写，指代玩家必须使用第二人称“你”。
            """.trimIndent()

            val jsonStructure = """
                {
                  "simulated_messages": [
                    {"character_id":"角色ID","type":"text","time":"yyyy-MM-dd HH:mm:ss","content":"消息正文","extra":"可选"}
                  ],
                  "simulated_moments": [
                    {"character_id":"角色ID","content":"朋友圈正文","has_image":false,"image_description":"","has_video":false,"video_description":"","time":"yyyy-MM-dd HH:mm:ss"}
                  ],
                  "simulated_tweets": [
                    {"character_id":"角色ID","content":"推特正文","has_image":false,"image_description":"","time":"yyyy-MM-dd HH:mm:ss"}
                  ]
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
                    personaPrompt = personaPrompt,
                    outputRequirement = outputRequirement,
                    jsonStructure = jsonStructure,
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
            val candidateMap = candidates.associateBy { it.characterId }
            val messageCount = saveSimulatedMessages(
                jsonArray = jsonObj.optJSONArray("simulated_messages") ?: JSONArray(),
                candidates = candidateMap,
                chatRepo = chatRepo,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                maxMessages = maxMessages
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
                twitterUsername = twitterProfile.username
            )
        }

        return byId.values.toList()
    }

    private fun saveSimulatedMessages(
        jsonArray: JSONArray,
        candidates: Map<String, OnlineCandidate>,
        chatRepo: ChatRepository,
        startTimeMillis: Long,
        endTimeMillis: Long,
        maxMessages: Int
    ): Int {
        val perCharacterCount = mutableMapOf<String, Int>()
        var savedCount = 0
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val characterId = obj.optString("character_id")
            val candidate = candidates[characterId] ?: continue
            val contactId = candidate.chatContactId ?: continue
            if (!candidate.canSendMessage) continue
            val currentCount = perCharacterCount[characterId] ?: 0
            if (currentCount >= maxMessages) continue
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
            perCharacterCount[characterId] = currentCount + 1
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
