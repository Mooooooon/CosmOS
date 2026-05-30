package com.moonlib.cosmos.data.interaction

import android.content.Context
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

        val isNativeGenerateContent = activeProfile.serviceType == AiServiceType.VERTEX ||
            (activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com"))

        val responseText = if (isNativeGenerateContent) {
            executeGeminiOfficial(context, baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged, activeProfile.serviceType, activeProfile.vertexRegion)
        } else {
            executeOpenAI(context, baseUrl, modelName, apiKey, temperature, systemPrompt, recentMerged, activeProfile.serviceType, charProfile.name, activeProfile.thinkingLevel)
        }

        // ── 4. 拦截并记录本次 AI 通讯日志 ──────────
        try {
            val sbPrompt = StringBuilder()
            sbPrompt.append(systemPrompt).append("\n\n=== 混合上下文记忆流（包含线上/线下） ===\n")
            for (i in recentMerged.indices) {
                val msg = recentMerged[i]
                val roleName = msg.roleNameForPrompt()
                val finalContent = if (i == recentMerged.lastIndex && msg.senderId == "user") {
                    msg.content + "\n(注意：你必须以指定的 JSON 格式输出回复，不要包含任何 markdown 块或废话)"
                } else {
                    msg.content
                }
                sbPrompt.append("$roleName: ${msg.prefix} $finalContent\n")
            }
            sbPrompt.append("请记住你是谁，直接输出你作为角色的下一组线下实体互动 JSON 回复：")

            val userInputText = recentMerged.lastOrNull { it.senderId == "user" && it.source != MergedMessageSource.DIARY }?.content ?: ""

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

        // ── 5. 解析状态卡更新字段并保存（按需静默更新） ──────────
        try {
            val cleanJson = cleanJsonResponse(responseText)
            val jsonObj = JSONObject(cleanJson)
            if (jsonObj.has("status") && !jsonObj.isNull("status")) {
                val statusObj = jsonObj.optJSONObject("status")
                if (statusObj != null) {
                    val statusMap = mutableMapOf<String, String>()
                    val keys = statusObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (!statusObj.isNull(key)) {
                            val value = statusObj.getString(key)
                            // 过滤无效或未变更的值并清洗格式
                            if (value.isNotBlank() && value != "null") {
                                statusMap[key] = cleanStatusValue(value)
                            }
                        }
                    }
                    if (statusMap.isNotEmpty()) {
                        val settingsRepo = InteractionSettingsRepository(context)
                        settingsRepo.updateCharacterStatus(characterId, statusMap)
                    }
                }
            }
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
        context: Context,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
        history: List<MergedMessage>,
        serviceType: AiServiceType = AiServiceType.GEMINI,
        vertexRegion: String = AiVertexConfig.DEFAULT_REGION
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = if (serviceType == AiServiceType.VERTEX) {
            AiVertexConfig.buildGenerateContentUrl(apiKey, vertexRegion, modelName)
        } else {
            "$base/v1beta/models/$modelName:generateContent?key=$apiKey"
        }
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Content-Type", "application/json")
        if (serviceType == AiServiceType.VERTEX) {
            conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
        }
        conn.doOutput = true
  
        val fullPromptBuilder = StringBuilder()
        fullPromptBuilder.append(systemPrompt).append("\n\n=== 混合上下文记忆流（包含线上/线下） ===\n")
        for (msg in history) {
            val roleName = msg.roleNameForPrompt()
            fullPromptBuilder.append("$roleName: ${msg.prefix} ${msg.content}\n")
        }
        fullPromptBuilder.append("请记住你是谁，直接输出你作为角色的下一组线下实体互动 JSON 回复：")

        // 动态加载状态配置组装 Schema
        val settingsRepo = InteractionSettingsRepository(context)
        val statusCardEnabled = settingsRepo.isStatusCardEnabled()
        val statusKeys = settingsRepo.getStatusKeys()

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
                if (statusCardEnabled && statusKeys.isNotEmpty()) {
                    val statusProperties = JSONObject()
                    for (key in statusKeys) {
                        statusProperties.put(key.name, JSONObject().apply {
                            put("type", "STRING")
                            put("nullable", true)
                        })
                    }
                    put("status", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", statusProperties)
                        put("nullable", true)
                    })
                }
            })
            put("required", JSONArray().apply {
                put("sender")
                put("replies")
                if (statusCardEnabled && statusKeys.isNotEmpty()) {
                    put("status")
                }
            })
        }

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
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
        context: Context,
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
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val messagesArray = JSONArray()
        
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        // 构造 messages 对话上下文历史聚合装填
        var i = 0
        val size = history.size
        while (i < size) {
            val msg = history[i]
            if (msg.source == MergedMessageSource.DIARY) {
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", "${msg.prefix} ${msg.content}")
                })
                i++
            } else if (msg.senderId == "user") {
                var content = "${msg.prefix} ${msg.content}"
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
                while (j < size && history[j].senderId != "user" && history[j].source != MergedMessageSource.DIARY) {
                    val aMsg = history[j]
                    val formattedTime = try {
                        sdf.format(java.util.Date(aMsg.timestamp))
                    } catch (e: Exception) {
                        sdf.format(java.util.Date())
                    }
                    
                    repliesArray.put(JSONObject().apply {
                        put("type", "text")
                        put("time", formattedTime)
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

        // 动态加载状态配置组装 Schema
        val settingsRepo = InteractionSettingsRepository(context)
        val statusCardEnabled = settingsRepo.isStatusCardEnabled()
        val statusKeys = settingsRepo.getStatusKeys()

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
            put("max_tokens", 2048)
            AiReasoningRequestOptions.applyTo(this, serviceType, modelName, thinkingLevel)
            
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
                        if (statusCardEnabled && statusKeys.isNotEmpty()) {
                            val statusProps = JSONObject()
                            val statusRequired = JSONArray()
                            for (key in statusKeys) {
                                statusProps.put(key.name, JSONObject().apply {
                                    put("type", JSONArray().apply { put("string"); put("null") })
                                })
                                statusRequired.put(key.name)
                            }
                            put("status", JSONObject().apply {
                                put("type", JSONArray().apply { put("object"); put("null") })
                                put("properties", statusProps)
                                put("required", statusRequired)
                                put("additionalProperties", false)
                            })
                        }
                    })
                    put("required", JSONArray().apply {
                        put("sender")
                        put("replies")
                        if (statusCardEnabled && statusKeys.isNotEmpty()) {
                            put("status")
                        }
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

    /**
     * 过滤状态部分的值，彻底去除中英文括号。
     */
    private fun cleanStatusValue(value: String): String {
        return value.replace(Regex("[()（）]"), "").trim()
    }

    private fun MergedMessage.roleNameForPrompt(): String {
        return when (source) {
            MergedMessageSource.DIARY -> "记忆"
            else -> if (senderId == "user") "用户" else "你"
        }
    }
}
