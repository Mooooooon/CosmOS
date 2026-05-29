package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.time.VirtualTimeManager
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
 * 职责单一：负责组织对话历史上下文，调用当前激活的 AI 模型并启用底层 JSON 通讯协议，
 * 获取、解析并保存拟真的角色多重回复消息，并同步推进虚拟世界时间。
 */
object ChatEngine {

    /**
     * 玩家发送消息并异步获取 AI 的回复消息列表。
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

        // 3. 构建深度结合聊天的 System Prompt
        val userNickname = chatRepo.getUserNickname()
        val rawPrompt = charProfile.prompt
        
        // 动态替换人设提示词里的变量
        val processedCharPrompt = rawPrompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", userNickname)

        // 获取并处理玩家本人的详细背景设定
        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val playerPrompt = playerProfile?.prompt ?: "普通玩家，无更多公开身份设定。"
        val processedPlayerPrompt = playerPrompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", userNickname)

        // 获取当前格式化的虚拟时间
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")

        val systemPrompt = """
            你现在正在扮演角色【${charProfile.name}】。
            以下是你的详细背景、性格以及外貌设定：
            ------------------------------------------------
            $processedCharPrompt
            ------------------------------------------------
            
            以下是你的聊天对象玩家【$userNickname】的详细设定（请利用这些设定来增强对话细节，实现完美互动）：
            ------------------------------------------------
            $processedPlayerPrompt
            ------------------------------------------------
            
            【聊天上下文信息】：
            1. 你当前正在通过 CosmOS 虚拟手机聊天软件与玩家【$userNickname】聊天。
            2. 在聊天中，你的昵称是【${contact.nickname}】，你的个性签名是【${contact.signature}】。
            3. 玩家的聊天昵称是【$userNickname】。
            4. 【当前虚拟世界的时间】是：$currentVirtualTimeStr。
            
            【核心对话要求】：
            1. 请必须百分之百扮演【${charProfile.name}】。绝对不可脱离角色（OOC）。
            2. 聊天交流应当符合手机聊天的特征：简洁、轻松、口语化。
            3. 单次回复可以是一条或多条连续消息（建议1到3条消息），每条消息字数应控制在1到3句话之内（建议单条不超过50字）。
            4. 绝对不可在回复中出现任何 emoji、颜文字或任何表情符号（如：😊, 😂, (๑•̀ㅂ•́)و✧, O(∩_∩)O 等）。所有消息内容必须完全使用纯文本进行表达和回复。
            
            【底层通信输出格式】：
            为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "sender": "${contact.nickname}",
              "replies": [
                {
                  "type": "text",
                  "time": "yyyy-MM-dd HH:mm:ss",
                  "content": "第一条纯文本消息内容，不能含有任何 emoji 或表情符号"
                },
                {
                  "type": "text",
                  "time": "yyyy-MM-dd HH:mm:ss",
                  "content": "第二条纯文本消息内容，不能含有任何 emoji 或表情符号"
                }
              ]
            }
            
            特别注意：
            - `replies` 数组内可以包含 1 到 3 条消息。
            - 每一条回复的 `time` 字段必须是符合 `yyyy-MM-dd HH:mm:ss` 格式的虚拟时间，且必须比上一个时间（以及当前虚拟时间：$currentVirtualTimeStr）更晚（建议每条之间间隔 5 秒到 1 分钟，代表思考和打字发送的间隔时间）。
            - 每一条回复的 `content` 必须是纯文本，严禁夹带任何表情和颜文字。
            - 你的最后一条回复的 `time` 将被作为新的虚拟世界时间。请据此来推进虚拟世界的时间！
            - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
        """.trimIndent()

        // 4. 提取最近 12 条消息作为上下文
        val allMessages = chatRepo.getMessages(contact.id)
        val recentMessages = allMessages.takeLast(12)

        // 识别是否为 Gemini 官方 API
        val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")

        val responseText = if (isGeminiOfficial) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMessages)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMessages)
        }

        // ── 拦截并记录本次 AI 通讯日志 ──────────────────────────
        try {
            val fullSentPrompt = if (isGeminiOfficial) {
                val fullPromptBuilder = StringBuilder()
                fullPromptBuilder.append(systemPrompt).append("\n\n=== 聊天历史纪录 ===\n")
                for (msg in recentMessages) {
                    val roleName = if (msg.senderId == "user") "玩家" else "你"
                    fullPromptBuilder.append("$roleName: ${msg.content}\n")
                }
                fullPromptBuilder.append("请记住你是谁，直接输出你作为角色的下一组符合 JSON 格式的回复：")
                fullPromptBuilder.toString()
            } else {
                val sb = StringBuilder()
                sb.append("[System Prompt]\n").append(systemPrompt).append("\n\n[Chat History]\n")
                for (msg in recentMessages) {
                    val role = if (msg.senderId == "user") "User" else "Assistant"
                    sb.append("$role: ${msg.content}\n")
                }
                sb.toString()
            }

            val userInputText = recentMessages.lastOrNull { it.senderId == "user" }?.content ?: ""

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
                
                list.add(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = contact.id,
                        content = content,
                        timestamp = finalTime
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 兜底保障：若 JSON 解析失败，将原回复作为单条普通消息并以默认时间偏置返回
            list.clear()
            list.add(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = contact.id,
                    content = jsonStr,
                    timestamp = defaultTimeMillis + 15000L
                )
            )
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
        history: List<ChatMessage>
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = "$base/v1beta/models/$modelName:generateContent?key=$apiKey"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
 
        // 组装 Gemini 内容结构：将 System Prompt + 历史纪录合为一段提示词发送
        val fullPromptBuilder = StringBuilder()
        fullPromptBuilder.append(systemPrompt).append("\n\n=== 聊天历史纪录 ===\n")
        for (msg in history) {
            val roleName = if (msg.senderId == "user") "玩家" else "你"
            fullPromptBuilder.append("$roleName: ${msg.content}\n")
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
        history: List<ChatMessage>
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

        // 2. 对话历史
        for (msg in history) {
            val role = if (msg.senderId == "user") "user" else "assistant"
            messagesArray.put(JSONObject().apply {
                put("role", role)
                put("content", msg.content)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
            // 开启 OpenAI/DeepSeek 官方 JSON Mode
            put("response_format", JSONObject().apply {
                put("type", "json_object")
            })
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
