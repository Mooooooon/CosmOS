package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiServiceType
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

        // 3. 构建深度结合聊天的 System Prompt
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()

        val userNickname = chatRepo.getUserNickname()
        
        // 获取用户档案，提取用户真实姓名
        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val playerRealName = playerProfile?.name ?: userNickname // 兜底使用网名
        
        val rawPrompt = charProfile.prompt
        
        // 动态替换人设提示词里的变量（使用真名替换 {{user}}）
        val processedCharPrompt = rawPrompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)

        val playerPrompt = playerProfile?.prompt ?: "普通用户，无更多公开身份设定。"
        val processedPlayerPrompt = playerPrompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)

        // 获取当前格式化的虚拟时间
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")
        val currentVirtualTimeWithWeekdayStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        val systemPrompt = """
            $mainPrompt
            
            你现在正在扮演角色【${charProfile.name}】。
            以下是你的详细背景、性格以及外貌设定：
            ------------------------------------------------
            $processedCharPrompt
            ------------------------------------------------
            
            以下是你的聊天对象用户【$userNickname】（真实姓名：$playerRealName）的详细设定（请利用这些设定来增强对话细节，实现完美互动）：
            ------------------------------------------------
            $processedPlayerPrompt
            ------------------------------------------------
            
            【手机聊天上下文信息】：
            1. 你当前正在通过 CosmOS 虚拟手机聊天软件与用户【$userNickname】远程在线聊天。
            2. 在聊天中，你的昵称是【${contact.nickname}】，你的个性签名是【${contact.signature}】。
            3. 用户的聊天昵称是【$userNickname】。
            4. 【当前虚拟世界的时间】是：$currentVirtualTimeWithWeekdayStr。
            
            【对话上下文（线上线下记忆融合）合并说明】：
            我们已经将你与用户的【线上聊天】历史和【线下面对应实体互动】历史按时间顺序合并在下方。
            - 带有 `[线上聊天]` 前缀的消息表示你们在虚拟手机聊天软件上的对话。
            - 带有 `[线下互动]` 前缀的消息表示你们在线下实体见面的动作对话，其中包含括弧动作描写。
            - 注意：你当前正在【线上聊天 APP】中回复用户。你的回复必须符合【线上远程手机聊天】的特征：简洁、轻松、口语化、纯对话文本、**严禁夹带任何括弧内的动作描写（如 `（看向对方）` 等）或表情符号**！你不需要在 JSON 的 `content` 字段中添加 `[线上聊天]` 前缀，直接进行回复即可。
            
            【核心对话要求】：
            1. 请必须百分之百扮演【${charProfile.name}】。绝对不可脱离角色（OOC）。
            2. 聊天交流应当符合手机聊天的特征：简洁、轻松、口语化。
            3. 单次回复可以是一条或多条连续消息（建议1到3条消息），每条消息字数应控制在1到3句话之内（建议单条不超过50字）。
            4. 绝对不可在回复中出现任何 emoji、颜文字或任何表情符号（如：😊, 😂, (๑•̀ㅂ•́)و✧, O(∩_∩)O 等）。所有消息内容必须完全使用纯文本进行表达和回复。
            
            【底层通信输出格式】：
            为了与其他 system 集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
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
            - 每一条回复的 `time` 字段必须是符合 `yyyy-MM-dd HH:mm:ss` 格式的虚拟时间，且必须比上一个时间（以及当前虚拟时间：$currentVirtualTimeStr）更晚（建议每条之间间隔 5 秒到 1 分钟，代表思考和打字发送 of 间隔时间）。
            - 每一条回复的 `content` 必须是纯文本，严禁夹带任何表情和颜文字。
            - 你的最后一条回复的 `time` 将被作为新的虚拟世界时间。请据此来推进虚拟世界的时间！
            - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
        """.trimIndent()

        // 4. 提取最近 15 条合并消息作为上下文（融合线上聊天与线下互动）
        val interactionRepo = InteractionRepository(context)
        val onlineMsgs = chatRepo.getMessages(contact.id)
        val offlineMsgs = interactionRepo.getMessages(contact.characterId)

        // 合并并以时间戳排序
        val mergedHistory = (
            onlineMsgs.map { MergedMessage(it.senderId, it.content, it.timestamp, isOnline = true) } +
            offlineMsgs.map { MergedMessage(it.senderId, it.content, it.timestamp, isOnline = false) }
        ).sortedBy { it.timestamp }

        // 取最近 15 条
        val recentMerged = mergedHistory.takeLast(15)

        // 识别是否为 Gemini 官方 API
        val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")

        val responseText = if (isGeminiOfficial) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged, activeProfile.serviceType, contact.nickname)
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
        senderName: String
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
