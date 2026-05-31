package com.moonlib.cosmos.data.interaction

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.ai.AiStatusUpdater
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiAuthorizationHeader
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiReasoningRequestOptions
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.AiVertexConfig
import com.moonlib.cosmos.data.chat.AiPromptHelper
import com.moonlib.cosmos.data.interaction.MergedMessageSource
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 线下实体互动 AI 回应引擎
 *
 * 职责单一：负责组织包含线上与线下混合的对话上下文，特化线下面层面实体互动 System Prompt，
 * 调用激活的 AI 模型并启用底层 JSON 结构化通讯协议，
 * 解析并保存带有括弧动作的拟真回应，并同步推进世界虚拟时间。
 * 支持 OpenAI 官方 Structured Outputs (json_schema) / DeepSeek JSON Mode / Gemini Structured Output (responseSchema)。
 */
object InteractionEngine {

    /**
     * 与角色进行实体互动并异步获取 AI 的肢体/言语回复列表。
     * @param context Android 上下文
     * @param characterId 当前互动的档案角色 ID
     * @return AI 回复的 InteractionMessage 对象列表
     */
    suspend fun getAiResponse(
        context: Context,
        characterId: String
    ): List<InteractionMessage> = withContext(Dispatchers.IO) {
        val interactionRepo = InteractionRepository(context)
        val chatRepo = com.moonlib.cosmos.data.chat.ChatRepository(context)
        val profileRepo = CharacterProfileRepository(context)

        // 1. 获取对应角色人设
        val charProfile = profileRepo.getProfiles().firstOrNull { it.id == characterId }
            ?: throw Exception("关联的角色档案不存在，请检查该角色人设。")

        // 2. 获取当前激活的 AI 服务商配置
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("未检测到激活的 AI 模型。请前往【系统设置】配置模型服务。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查。")
        }

        // 3. Build system prompt and merge history via AiPromptHelper
        val (systemPrompt, recentMerged) = AiPromptHelper.buildPromptAndHistory(
            context = context,
            charProfile = charProfile,
            sceneType = AiSceneType.INTERACTION
        )

        val historyText = AiHistoryFormatter.formatMergedMessages(recentMerged)
        val userInputText = recentMerged.lastOrNull { it.senderId == "user" && it.source.isDirectConversation() }?.content ?: ""
        val result = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.INTERACTION,
                systemPrompt = SystemPromptRepository(context).getMainPromptContent(),
                personaPrompt = systemPrompt.removePrefix(SystemPromptRepository(context).getMainPromptContent()).trim(),
                outputRequirement = "你当前正在与用户进行线下面对面实体互动。每条回复必须包含中文括号动作描写，严禁 emoji 和颜文字；如果状态卡发生改变，按需输出 status。",
                jsonStructure = """{"sender":"${charProfile.name}","replies":[{"type":"text","time":"yyyy-MM-dd HH:mm:ss","content":"（动作描写）回复内容"}],"status":{"词条名称":"更新值"}}""",
                historyText = historyText,
                userInput = userInputText,
                logCharacterName = charProfile.name,
                logUserInput = userInputText,
                responseSchema = AiJsonSchemaFactory.chatRepliesSchema("cosmos_interaction_replies")
            )
        )
        val responseText = result.rawResponse

        // ── 5. 解析状态卡更新字段并保存（按需静默更新） ──────────
        try {
            val cleanJson = AiResponseCleaner.cleanJson(responseText)
            val jsonObj = JSONObject(cleanJson)
            AiStatusUpdater.updateSingleCharacter(context, characterId, jsonObj)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. 组装、解析并保存 AI 的回复消息列表
        val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
        val aiMessages = parseAiResponseJson(responseText, characterId, currentVirtualTime)

        for (msg in aiMessages) {
            interactionRepo.saveMessage(characterId, msg)
        }

        // 7. 推进虚拟时间为最后一条回复的时间
        if (aiMessages.isNotEmpty()) {
            val maxTimestamp = aiMessages.maxOf { it.timestamp }
            VirtualTimeManager.updateTime(maxTimestamp)
        }

        aiMessages
    }

    private fun parseAiResponseJson(
        jsonStr: String,
        characterId: String,
        defaultTimeMillis: Long
    ): List<InteractionMessage> {
        val list = mutableListOf<InteractionMessage>()
        try {
            val cleanJson = AiResponseCleaner.cleanJson(jsonStr)
            val jsonObj = JSONObject(cleanJson)
            val repliesArray = jsonObj.getJSONArray("replies")
            
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            var lastTime = defaultTimeMillis
            
            for (i in 0 until repliesArray.length()) {
                val replyObj = repliesArray.getJSONObject(i)
                val timeStr = replyObj.optString("time", "")
                val content = replyObj.optString("content", "")
                
                if (content.isBlank()) continue

                val parsedTime = try {
                    if (timeStr.isNotBlank()) {
                        sdf.parse(timeStr)?.time ?: (lastTime + 15000L)
                    } else {
                        lastTime + 15000L
                    }
                } catch (e: Exception) {
                    lastTime + 15000L
                }
                
                val finalTime = if (parsedTime > lastTime) parsedTime else lastTime + 5000L
                lastTime = finalTime
                
                list.add(
                    InteractionMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = characterId,
                        content = content,
                        timestamp = finalTime
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            list.clear()
            
            // 1. 尝试清洗可能混入的 thinking 标签，获取纯文本回复
            var rawText = jsonStr.trim()
            if (rawText.contains("</thinking>")) {
                val parts = rawText.split("</thinking>")
                rawText = parts.last().trim()
            } else if (rawText.contains("<thinking>")) {
                val index = rawText.indexOf("<thinking>")
                if (index != -1) {
                    rawText = rawText.substring(0, index).trim()
                }
            }
            
            // 2. 将纯文本按双换行或单换行切分
            val rawLines = rawText.split(Regex("\n+"))
            val cleanLines = rawLines.map { it.trim() }.filter { it.isNotBlank() }
            
            if (cleanLines.isNotEmpty()) {
                var lastTime = defaultTimeMillis
                for (line in cleanLines) {
                    val finalTime = lastTime + 15000L
                    lastTime = finalTime
                    list.add(
                        InteractionMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = characterId,
                            content = line,
                            timestamp = finalTime
                        )
                    )
                }
            } else {
                list.add(
                    InteractionMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = characterId,
                        content = jsonStr,
                        timestamp = defaultTimeMillis + 15000L
                    )
                )
            }
        }
        
        if (list.isEmpty()) {
            list.add(
                InteractionMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = characterId,
                    content = jsonStr,
                    timestamp = defaultTimeMillis + 15000L
                )
            )
        }
        return list
    }

    /**
     * 过滤状态部分的值，彻底去除中英文括号。
     */
    private fun MergedMessageSource.isDirectConversation(): Boolean {
        return this == MergedMessageSource.CHAT || this == MergedMessageSource.INTERACTION
    }
}
