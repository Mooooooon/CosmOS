package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.interaction.MergedMessage
import com.moonlib.cosmos.data.interaction.MergedMessageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * 聊天 AI 回复响应引擎
 *
 * 职责单一：负责组织包含线上聊天与线下互动的融合对话历史上下文，调用当前激活的 AI 模型并启用底层 JSON 通讯协议，
 * 获取、解析并保存拟真的角色多重回复消息，并同步推进虚拟世界时间。
 */
object ChatEngine {

    /**
     * 用户发送消息并异步获取 AI 的回复消息列表。
     * @param context Android 上下文
     * @param contact 当前聊天的联系人
     * @return AI 回复的 ChatMessage 对象列表
     */
    suspend fun getAiResponse(
        context: Context,
        contact: ChatContact
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        val chatRepo = ChatRepository(context)
        val profileRepo = CharacterProfileRepository(context)

        // 1. 获取对应角色人设
        val charProfile = profileRepo.getProfiles().firstOrNull { it.id == contact.characterId }
            ?: throw Exception("关联的角色档案不存在，请检查或重新编辑该联系人资料。")

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
            sceneType = AiSceneType.CHAT,
            chatNickname = contact.nickname,
            chatSignature = contact.signature
        )

        // Gemini 与 Vertex 都走原生 generateContent，而不是 OpenAI 兼容接口。
        val historyText = recentMerged.joinToString("\n") { msg ->
            "${msg.roleNameForPrompt()}: ${msg.prefix} ${msg.content}"
        }
        val userInputText = recentMerged.lastOrNull { it.senderId == "user" && it.source.isDirectConversation() }?.content ?: ""
        val result = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.CHAT,
                systemPrompt = SystemPromptRepository(context).getMainPromptContent(),
                personaPrompt = systemPrompt.removePrefix(SystemPromptRepository(context).getMainPromptContent()).trim(),
                outputRequirement = "你当前正在聊天 App 内回复用户。回复必须符合线上远程手机聊天特征：简洁、轻松、口语化，严禁动作括号、emoji、颜文字。",
                jsonStructure = """{"sender":"${contact.nickname}","replies":[{"type":"text","time":"yyyy-MM-dd HH:mm:ss","content":"回复内容"}]}""",
                historyText = historyText,
                userInput = userInputText,
                logCharacterName = charProfile.name,
                logUserInput = userInputText,
                responseSchema = AiJsonSchemaFactory.chatRepliesSchema("cosmos_chat_replies")
            )
        )
        val responseText = result.rawResponse

        // 5. 组装、解析并保存 AI 的回复消息列表
        val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
        val aiMessages = parseAiResponseJson(responseText, contact, currentVirtualTime)

        for (msg in aiMessages) {
            chatRepo.saveMessage(contact.id, msg)
        }

        // 6. 推进虚拟时间为最后一条回复的时间
        if (aiMessages.isNotEmpty()) {
            val maxTimestamp = aiMessages.maxOf { it.timestamp }
            VirtualTimeManager.updateTime(maxTimestamp)
        }

        aiMessages
    }

    /**
     * 将 AI 响应解析为 ChatMessage 列表，包含高度健壮的容错机制
     */
    private fun parseAiResponseJson(
        jsonStr: String,
        contact: ChatContact,
        defaultTimeMillis: Long
    ): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        try {
            val cleanJson = AiResponseCleaner.cleanJson(jsonStr)
            val jsonObj = JSONObject(cleanJson)
            val repliesArray = jsonObj.getJSONArray("replies")
            
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            var lastTime = defaultTimeMillis
            
            for (i in 0 until repliesArray.length()) {
                val replyObj = repliesArray.getJSONObject(i)
                val type = replyObj.optString("type", "text")
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
                
                // 确保时间始终是严格递增的线性时间
                val finalTime = if (parsedTime > lastTime) parsedTime else lastTime + 5000L
                lastTime = finalTime
                
                val extraVal = if (type == "red_packet") {
                    replyObj.optString("extra", "恭喜发财，大吉大利")
                } else if (type == "transfer") {
                    "sent"
                } else {
                    null
                }

                list.add(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = contact.id,
                        content = content,
                        timestamp = finalTime,
                        type = type,
                        extra = extraVal
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
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = contact.id,
                            content = line,
                            timestamp = finalTime
                        )
                    )
                }
            } else {
                list.add(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = contact.id,
                        content = jsonStr,
                        timestamp = defaultTimeMillis + 15000L
                    )
                )
            }
        }
        
        // 若数组为空，也提供兜底
        if (list.isEmpty()) {
            list.add(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = contact.id,
                    content = jsonStr,
                    timestamp = defaultTimeMillis + 15000L
                )
            )
        }
        
        return list
    }

    private fun MergedMessage.roleNameForPrompt(): String {
        return when (source) {
            MergedMessageSource.DIARY -> "记忆"
            MergedMessageSource.TWITTER -> "记忆"
            else -> if (senderId == "user") "用户" else "你"
        }
    }

    private fun MergedMessageSource.isDirectConversation(): Boolean {
        return this == MergedMessageSource.CHAT || this == MergedMessageSource.INTERACTION
    }
}
