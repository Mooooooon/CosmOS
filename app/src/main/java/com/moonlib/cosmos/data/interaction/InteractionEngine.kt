package com.moonlib.cosmos.data.interaction

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiHistoryItem
import com.moonlib.cosmos.data.ai.AiHistorySource
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.ai.AiStatusUpdater
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

        val promptData = buildInteractionPromptData(
            context = context,
            charProfile = charProfile
        )
        val recentMerged = promptData.recentMergedHistory

        val historyText = AiHistoryFormatter.formatHistoryItems(recentMerged)
        val userInputText = recentMerged.lastOrNull { it.senderId == "user" && it.source.isDirectConversation() }?.content ?: ""
        val result = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.INTERACTION,
                systemPrompt = promptData.systemPrompt,
                personaPrompt = promptData.personaPrompt,
                outputRequirement = promptData.outputRequirement,
                jsonStructure = promptData.jsonStructure,
                historyText = historyText,
                statusCard = promptData.statusPrompt,
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
    private fun AiHistorySource.isDirectConversation(): Boolean {
        return this == AiHistorySource.CHAT || this == AiHistorySource.INTERACTION
    }

    private data class InteractionPromptData(
        val systemPrompt: String,
        val personaPrompt: String,
        val outputRequirement: String,
        val jsonStructure: String,
        val statusPrompt: String,
        val recentMergedHistory: List<AiHistoryItem>
    )

    private fun buildInteractionPromptData(
        context: Context,
        charProfile: CharacterProfile
    ): InteractionPromptData {
        val chatRepo = ChatRepository(context)
        val profileRepo = CharacterProfileRepository(context)
        val userNickname = chatRepo.getUserNickname()
        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val playerRealName = playerProfile?.name ?: userNickname
        val currentVirtualTime = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")
        val currentVirtualTimeWithWeekday = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")
        val processedCharPrompt = charProfile.prompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)
        val processedPlayerPrompt = (playerProfile?.prompt ?: "普通用户，无更多公开身份设定。")
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)
        val statusPrompt = buildInteractionStatusPrompt(context, charProfile)

        return InteractionPromptData(
            systemPrompt = SystemPromptRepository(context).getMainPromptContent(),
            personaPrompt = """
                你现在正在扮演角色【${charProfile.name}】。
                
                【角色人设】
                $processedCharPrompt
                
                【用户人设】
                用户昵称：$userNickname
                用户真实姓名：$playerRealName
                $processedPlayerPrompt
            """.trimIndent(),
            outputRequirement = """
                当前场景：线下面对面实体互动。
                你正在与用户【$playerRealName】进行实体互动，而不是手机聊天。
                当前虚拟世界时间：$currentVirtualTimeWithWeekday。
                
                回复要求：
                1. 必须百分之百扮演【${charProfile.name}】，不可 OOC。
                2. 每条回复必须包含中文小括号动作描写，例如“（看向你，轻声说）我在听。”。
                3. 动作、神态、语气、心理和姿势应具体写实，符合人设。
                4. 指代用户/玩家必须使用第二人称“你”，禁止使用“他/她”代指用户。
                5. 严禁 emoji、颜文字和表情符号。
                6. 单次回复 1 到 3 条，每条 1 到 3 句话，建议单条不超过 60 字。
                7. 如果状态卡发生改变，按“状态卡”段落要求在 JSON 外层输出 status。
            """.trimIndent(),
            jsonStructure = """
                {
                  "sender": "${charProfile.name}",
                  "replies": [
                    {
                      "type": "text",
                      "time": "yyyy-MM-dd HH:mm:ss",
                      "content": "（动作描写）回复内容"
                    }
                  ]${if (statusPrompt.isNotBlank()) ",\n                  \"status\": {\n                    \"词条名称\": \"仅当该词条状态发生改变时更新的值，未改变的词条不输出或设为 null\"\n                  }" else ""}
                }
                
                约束：
                - replies 数组包含 1 到 3 条消息。
                - time 必须晚于当前虚拟时间 $currentVirtualTime，并符合 yyyy-MM-dd HH:mm:ss。
                - content 必须带中文小括号动作描写。
                - status 值不能包含中文或英文小括号。
                - 只返回纯 JSON，不要 markdown 代码块或解释文本。
            """.trimIndent(),
            statusPrompt = statusPrompt,
            recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
                context = context,
                charProfiles = listOf(charProfile),
                maxContextSize = AiSettingsRepository(context).getMaxContextSize(),
                playerName = playerRealName
            )
        )
    }

    private fun buildInteractionStatusPrompt(context: Context, charProfile: CharacterProfile): String {
        val repo = InteractionSettingsRepository(context)
        val statusKeys = repo.getStatusKeys()
        if (!repo.isStatusCardEnabled() || statusKeys.isEmpty()) return ""

        val currentStatus = repo.getCharacterStatus(charProfile.id)
        val statusBulletPoints = statusKeys.joinToString("\n") { key ->
            val currentVal = currentStatus[key.name] ?: "未知"
            "- 「${key.name}」（含义解释：${key.description}）：当前状态值是「$currentVal」"
        }
        return """
            当前互动角色状态卡已开启，请维护以下状态词条：
            $statusBulletPoints
            
            状态更新要求：
            1. 只有身体姿势、动作、神态、物理位置、服装衣着等发生改变时才输出 status。
            2. 未改变的词条不要输出，或设为 null。
            3. 状态值必须是纯描述，不能包含中文小括号或英文小括号。
            4. 状态值中指代用户必须使用第二人称“你”。
        """.trimIndent()
    }
}
