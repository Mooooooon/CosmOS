package com.moonlib.cosmos.data.interaction

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiServiceType
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

        // 3. 构建深度结合实体动作互动的 System Prompt
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
            
            以下是你的互动对象用户【$playerRealName】的详细设定（请利用这些设定来增强对话细节，实现完美互动）：
            ------------------------------------------------
            $processedPlayerPrompt
            ------------------------------------------------
            
            【实体互动（线下面面对面互动）上下文信息】：
            1. 你当前正在与用户【$playerRealName】进行【实体线下面对面互动】（而非通过手机聊天软件）。
            2. 用户的真实姓名是【$playerRealName】。
            3. 【当前虚拟世界的时间】是：$currentVirtualTimeWithWeekdayStr。
            
            【对话上下文（线上线下记忆融合）合并说明】：
            我们已经将你与用户的【线上聊天】历史和【线下面面对面实体互动】历史按时间顺序合并在下方。
            - 带有 `[线上聊天]` 前缀的消息表示你们先前在手机软件上的远程聊天。
            - 带有 `[线下互动]` 前缀的消息表示你们在现实线下见面的动作对话，其中包含括弧动作描写。
            - 注意：你现在正在与用户进行【线下面面对面实体互动】。因此你作为角色的下一组回复中，**除了言语对话，还必须夹带丰富的肢体动作、神态、语气、心理或眼神等描写（写在中文小括号 `（动作描写）` 内，例如：`（看向对方，脸上有些疑惑）带了，怎么啦？`）**。
            
            【核心对话要求】：
            1. 请必须百分之百扮演【${charProfile.name}】。绝对不可脱离角色（OOC）。
            2. 这是一个面对面的场景，你的动作应当是生动、写实、符合人设神态的。
            3. 你的每一句回复，除纯说话内容外，**必须带有括号动作描写**。例如：
               - `（摸了摸自己的口袋，神色微微有些慌张）坏了，东西好像丢了。`
               - `（眼神游离，不好意思地揉了揉头发）那个，我刚才没听清，能再说一遍吗？`
            4. 单次回复可以是一条或多条连续消息（建议1到3条），每条字数控制在1到3句话（建议单条不超过60字）。
            5. 绝对不可在回复中出现任何 emoji、颜文字或任何表情符号。所有非动作描写的对话必须是纯文本。
            
            【底层通信输出格式】：
            为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "sender": "${charProfile.name}",
              "replies": [
                {
                  "type": "text",
                  "time": "yyyy-MM-dd HH:mm:ss",
                  "content": "（动作描写）第一条动作加对话内容，不能含有任何 emoji"
                },
                {
                  "type": "text",
                  "time": "yyyy-MM-dd HH:mm:ss",
                  "content": "（动作描写）第二条动作加对话内容，不能含有任何 emoji"
                }
              ]
            }
            
            特别注意：
            - `replies` 数组内可以包含 1 到 3 条消息。
            - 每一条回复的 `time` 必须是符合 `yyyy-MM-dd HH:mm:ss` 格式的虚拟时间，且必须比上一个时间（以及当前虚拟时间：$currentVirtualTimeStr）更晚（建议每条之间间隔 5 秒到 1 分钟，代表动作和说话的物理间隔）。
            - 每一条回复的 `content` 必须带有中文括号 `（动作描写）`，严禁夹带任何表情和颜文字。
            - 你的最后一条回复的 `time` 将被作为新的虚拟世界时间。请据此来推进虚拟世界的时间！
            - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
        """.trimIndent()

        // 4. 获取合并上下文（包含线上聊天与线下互动）
        val contact = chatRepo.getContacts().firstOrNull { it.characterId == characterId }
        val onlineMsgs = if (contact != null) chatRepo.getMessages(contact.id) else emptyList()
        val offlineMsgs = interactionRepo.getMessages(characterId)
        
        // 合并并以时间戳排序
        val mergedHistory = (
            onlineMsgs.map { MergedMessage(it.senderId, it.content, it.timestamp, isOnline = true) } +
            offlineMsgs.map { MergedMessage(it.senderId, it.content, it.timestamp, isOnline = false) }
        ).sortedBy { it.timestamp }
        
        // 截取最近 15 条
        val recentMerged = mergedHistory.takeLast(15)

        // 识别是否为 Gemini 官方 API
        val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")

        val responseText = if (isGeminiOfficial) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged, activeProfile.serviceType, charProfile.name)
        }

        // ── 5. 拦截并记录本次 AI 通讯日志（保存到系统设置的日志查看器中，极其重要） ──────────
        try {
            val sbPrompt = StringBuilder()
            sbPrompt.append(systemPrompt).append("\n\n=== 混合上下文记忆流（包含线上/线下） ===\n")
            for (i in recentMerged.indices) {
                val msg = recentMerged[i]
                val roleName = if (msg.senderId == "user") "用户" else "你"
                val prefix = if (msg.isOnline) "[线上聊天]" else "[线下互动]"
                val finalContent = if (i == recentMerged.lastIndex && msg.senderId == "user") {
                    msg.content + "\n(注意：你必须以指定的 JSON 格式输出回复，不要包含任何 markdown 块或废话)"
                } else {
                    msg.content
                }
                sbPrompt.append("$roleName: $prefix $finalContent\n")
            }
            sbPrompt.append("请记住你是谁，直接输出你作为角色的下一组线下实体互动 JSON 回复：")

            val userInputText = recentMerged.lastOrNull { it.senderId == "user" }?.content ?: ""

            val logRepo = com.moonlib.cosmos.data.settings.AiLogRepository(context)
            logRepo.saveLog(
                characterName = charProfile.name,
                modelName = modelName,
                userInput = userInputText,
                aiResponse = responseText,
                prompt = sbPrompt.toString()
            )
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
        val start = trimmed.indexOf("{")
        val end = trimmed.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
    }

    private fun parseAiResponseJson(
        jsonStr: String,
        characterId: String,
        defaultTimeMillis: Long
    ): List<InteractionMessage> {
        val list = mutableListOf<InteractionMessage>()
        try {
            val cleanJson = cleanJsonResponse(jsonStr)
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
  
        val fullPromptBuilder = StringBuilder()
        fullPromptBuilder.append(systemPrompt).append("\n\n=== 混合上下文记忆流（包含线上/线下） ===\n")
        for (msg in history) {
            val roleName = if (msg.senderId == "user") "用户" else "你"
            val prefix = if (msg.isOnline) "[线上聊天]" else "[线下互动]"
            fullPromptBuilder.append("$roleName: $prefix ${msg.content}\n")
        }
        fullPromptBuilder.append("请记住你是谁，直接输出你作为角色的下一组线下实体互动 JSON 回复：")

        // 构造符合 Gemini 官方标准的 responseSchema 结构
        val replySchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("type", JSONObject().apply { put("type", "STRING") })
                put("time", JSONObject().apply { put("type", "STRING") })
                put("content", JSONObject().apply { put("type", "STRING") })
            })
            put("required", JSONArray().apply {
                put("type")
                put("time")
                put("content")
            })
        }

        val geminiSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("sender", JSONObject().apply { put("type", "STRING") })
                put("replies", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", replySchema)
                })
            })
            put("required", JSONArray().apply {
                put("sender")
                put("replies")
            })
        }

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
                put("responseSchema", geminiSchema)
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
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val messagesArray = JSONArray()
        
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        // 构造 messages 对话上下文历史聚合装填，并在最后一条用户消息内容末尾注入 JSON 强制要求指令，合并连续助手回复防止模仿偏差
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
