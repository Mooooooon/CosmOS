package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.interaction.MergedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

        // 识别是否为 Gemini 官方 API
        val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")

        val responseText = if (isGeminiOfficial) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged, activeProfile.serviceType, contact.nickname, activeProfile.thinkingLevel)
        }

        // ── 拦截并记录本次 AI 通讯日志 ──────────────────────────
        try {
            val fullSentPrompt = if (isGeminiOfficial) {
                val fullPromptBuilder = StringBuilder()
                fullPromptBuilder.append(systemPrompt).append("\n\n=== 融合历史记忆（线上/线下） ===\n")
                for (msg in recentMerged) {
                    val roleName = if (msg.senderId == "user") "用户" else "你"
                    val prefix = if (msg.isOnline) "[线上聊天]" else "[线下互动]"
                    fullPromptBuilder.append("$roleName: $prefix ${msg.content}\n")
                }
                fullPromptBuilder.append("请记住你是谁，直接输出你作为角色的下一组符合 JSON 格式的回复：")
                fullPromptBuilder.toString()
            } else {
                val sb = StringBuilder()
                sb.append("[System Prompt]\n").append(systemPrompt).append("\n\n[Unified Chat/Interaction History]\n")
                for (msg in recentMerged) {
                    val role = if (msg.senderId == "user") "User" else "Assistant"
                    val prefix = if (msg.isOnline) "[线上聊天]" else "[线下互动]"
                    sb.append("$role: $prefix ${msg.content}\n")
                }
                sb.toString()
            }

            val userInputText = recentMerged.lastOrNull { it.senderId == "user" }?.content ?: ""

            val logRepo = com.moonlib.cosmos.data.settings.AiLogRepository(context)
            logRepo.saveLog(
                characterName = charProfile.name,
                modelName = modelName,
                userInput = userInputText,
                aiResponse = responseText,
                prompt = fullSentPrompt
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
     * 清洗 AI 输出的文本，提取合法的 JSON 字符串
     */
    private fun cleanJsonResponse(rawResponse: String): String {
        var trimmed = rawResponse.trim()
        if (trimmed.startsWith("```")) {
            val firstLineEnd = trimmed.indexOf("\n")
            if (firstLineEnd != -1) {
                trimmed = trimmed.substring(firstLineEnd + 1)
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length - 3)
            }
        }
        trimmed = trimmed.trim()
        
        // 查找第一个 '{' 和最后一个 '}' 之间的内容，确保能够解析包裹的 JSON
        val start = trimmed.indexOf("{")
        val end = trimmed.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
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
            val cleanJson = cleanJsonResponse(jsonStr)
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

    /**
     * 调用 Gemini 官方 API（带 Structured Output 配置）
     */
    private fun executeGeminiOfficial(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
        history: List<MergedMessage>
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = "$base/v1beta/models/$modelName:generateContent?key=$apiKey"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
  
        // 组装 Gemini 内容结构：将 System Prompt + 历史纪录合为一段提示词发送
        val fullPromptBuilder = StringBuilder()
        fullPromptBuilder.append(systemPrompt).append("\n\n=== 融合历史记忆（线上/线下） ===\n")
        for (msg in history) {
            val roleName = if (msg.senderId == "user") "用户" else "你"
            val prefix = if (msg.isOnline) "[线上聊天]" else "[线下互动]"
            fullPromptBuilder.append("$roleName: $prefix ${msg.content}\n")
        }
        fullPromptBuilder.append("请记住你是谁，直接输出你作为角色的下一组符合 JSON 格式的回复：")

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply {
                            put("text", fullPromptBuilder.toString())
                        }
                    ))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
                put("maxOutputTokens", 2048)
                // 开启 Gemini 官方 Structured Output (JSON Mode)
                put("responseMimeType", "application/json")
            })
        }

        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text").trim()
        } else {
            val errorText = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }
            throw Exception("AI接口报错 HTTP $responseCode: ${errorText.take(120)}")
        }
    }

    /**
     * 调用 OpenAI/DeepSeek 标准接口（带 JSON Mode 配置）
     */
    private fun executeOpenAI(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
        history: List<MergedMessage>,
        serviceType: AiServiceType,
        senderName: String,
        thinkingLevel: String
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        // 构造 messages 数组
        val messagesArray = JSONArray()
        
        // 1. 系统角色设定
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        // 2. 对话历史聚合装填，并在最后一条用户消息内容末尾注入 JSON 强制要求指令，合并连续助手回复防止模仿偏差
        var i = 0
        val size = history.size
        while (i < size) {
            val msg = history[i]
            if (msg.senderId == "user") {
                val prefix = if (msg.isOnline) "[线上聊天]" else "[线下互动]"
                var content = "$prefix ${msg.content}"
                if (i == size - 1) {
                    content += "\n(注意：你必须以指定的 JSON 格式输出回复，不要包含 any markdown 块或废话)"
                }
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                })
                i++
            } else {
                // 聚合连续的助手消息气泡到同一个 JSON 中
                val repliesArray = JSONArray()
                var j = i
                while (j < size && history[j].senderId != "user") {
                    val aMsg = history[j]
                    val formattedTime = try {
                        sdf.format(java.util.Date(aMsg.timestamp))
                    } catch (e: Exception) {
                        sdf.format(java.util.Date())
                    }
                    
                    repliesArray.put(JSONObject().apply {
                        put("type", "text")
                        put("time", formattedTime)
                        // 注意：为了避免模型在吐出的最新 JSON 的 content 中强加 [线下互动] / [线上聊天] 前缀，
                        // 我们必须在此直接装填干净的内容，绝对不加入任何前缀。
                        put("content", aMsg.content)
                    })
                    j++
                }
                
                val assistantJson = JSONObject().apply {
                    put("sender", senderName)
                    put("replies", repliesArray)
                }
                
                messagesArray.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", assistantJson.toString())
                })
                
                i = j
            }
        }

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
            put("max_tokens", 2048)
            if (thinkingLevel != "off") {
                put("reasoning_effort", thinkingLevel)
            }
            
            // 区分服务商，选择最适合的 JSON 输出配置
            if (serviceType == AiServiceType.OPEN_AI) {
                // OpenAI 官方标准 Structured Outputs (strict json_schema)
                val replySchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("type", JSONObject().apply { put("type", "string") })
                        put("time", JSONObject().apply { put("type", "string") })
                        put("content", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply {
                        put("type")
                        put("time")
                        put("content")
                    })
                    put("additionalProperties", false)
                }

                val openAiSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("sender", JSONObject().apply { put("type", "string") })
                        put("replies", JSONObject().apply {
                            put("type", "array")
                            put("items", replySchema)
                        })
                    })
                    put("required", JSONArray().apply {
                        put("sender")
                        put("replies")
                    })
                    put("additionalProperties", false)
                }

                put("response_format", JSONObject().apply {
                    put("type", "json_schema")
                    put("json_schema", JSONObject().apply {
                        put("name", "ai_chat_response")
                        put("strict", true)
                        put("schema", openAiSchema)
                    })
                })
            } else {
                // DeepSeek / 其他服务商的标准 JSON Mode (json_object)
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }
        }

        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            val choices = json.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content").trim()
        } else {
            val errorText = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }
            throw Exception("AI接口报错 HTTP $responseCode: ${errorText.take(120)}")
        }
    }
}
