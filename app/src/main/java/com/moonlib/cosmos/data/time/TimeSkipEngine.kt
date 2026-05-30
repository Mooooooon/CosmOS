package com.moonlib.cosmos.data.time

import android.content.Context
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiLogRepository
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 时间跳过离线消息模拟引擎
 *
 * 职责单一：负责联合已建立会话的角色档案上下文、玩家活动状态及全局参数，
 * 调用全局 AI 模型模拟离线消息，并将模拟的消息存入数据库，最终推进虚拟时间。
 */
object TimeSkipEngine {

    data class TimeSkipResult(
        val success: Boolean,
        val simulatedMessageCount: Int,
        val errorMessage: String? = null
    )

    /**
     * 推进虚拟时间并模拟该期间联系人可能发送的消息
     * @param context Android 上下文
     * @param startTimeMillis 起始虚拟时间戳（毫秒）
     * @param endTimeMillis 结束（目标）虚拟时间戳（毫秒）
     * @param userActivity 用户在这段时间内的行为（如：睡觉）
     */
    suspend fun executeTimeSkip(
        context: Context,
        startTimeMillis: Long,
        endTimeMillis: Long,
        userActivity: String
    ): TimeSkipResult = withContext(Dispatchers.IO) {
        try {
            val profileRepo = CharacterProfileRepository(context)
            val chatRepo = ChatRepository(context)
            val aiSettingsRepo = AiSettingsRepository(context)
            
            // 1. 获取所有现有聊天联系人
            val contacts = chatRepo.getContacts()
            if (contacts.isEmpty()) {
                // 没有联系人，无须模拟，直接推进时间
                VirtualTimeManager.updateTime(endTimeMillis)
                return@withContext TimeSkipResult(true, 0)
            }

            // 获取所有非玩家角色档案
            val allProfiles = profileRepo.getProfiles()
            val playerProfile = allProfiles.firstOrNull { it.isPlayer }
            val playerRealName = playerProfile?.name ?: chatRepo.getUserNickname()

            // 过滤出在联系人列表里、且拥有对应 CharacterProfile 的非玩家角色
            val candidateNpcs = contacts.mapNotNull { contact ->
                val profile = allProfiles.firstOrNull { it.id == contact.characterId && !it.isPlayer }
                if (profile != null) Pair(contact, profile) else null
            }

            if (candidateNpcs.isEmpty()) {
                // 没有匹配的角色联系人，无须模拟，直接推进时间
                VirtualTimeManager.updateTime(endTimeMillis)
                return@withContext TimeSkipResult(true, 0)
            }

            // 2. 获取全局最大单人模拟条数限制
            val timeSkipMaxMessages = aiSettingsRepo.getTimeSkipMaxMessages()

            // 3. 格式化起始与结束时间
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
            val startTimeStr = sdf.format(Date(startTimeMillis))
            val endTimeStr = sdf.format(Date(endTimeMillis))

            // 4. 组装分类好的候选角色详细背景与上下文记录
            val npcContextsBuilder = StringBuilder()
            for ((contact, npc) in candidateNpcs) {
                npcContextsBuilder.append("角色 ID (character_id): ${npc.id}\n")
                npcContextsBuilder.append("角色名字: ${npc.name}\n")
                npcContextsBuilder.append("在聊天App中的联系人昵称: ${contact.nickname}\n")
                
                // 替换 {{char}} 和 {{user}}
                val npcPrompt = npc.prompt
                    .replace("{{char}}", npc.name)
                    .replace("{{user}}", playerRealName)
                npcContextsBuilder.append("【人设与日常作息设定】：\n$npcPrompt\n")

                // 获取最近 5 条聊天历史
                val messages = chatRepo.getMessages(contact.id).takeLast(5)
                val historyText = if (messages.isEmpty()) {
                    "（此前暂无聊天记录）"
                } else {
                    messages.joinToString("\n") { msg ->
                        val role = if (msg.senderId == contact.id) npc.name else "用户"
                        val typePrefix = when (msg.type) {
                            "image" -> "[图片: ${msg.content}]"
                            "video" -> "[视频: ${msg.content}]"
                            "red_packet" -> "[红包: ${msg.content}元, 留言: ${msg.extra ?: "恭喜发财"}]"
                            "transfer" -> "[转账: ${msg.content}元]"
                            "location" -> "[位置: ${msg.content}]"
                            else -> msg.content
                        }
                        "- [${sdf.format(Date(msg.timestamp))}] $role: $typePrefix"
                    }
                }
                npcContextsBuilder.append("【最近聊天历史记录（最新5条）】：\n$historyText\n")
                npcContextsBuilder.append("=========================================\n\n")
            }

            // 5. 获取全局系统提示词基底
            val systemPromptRepo = SystemPromptRepository(context)
            val mainPrompt = systemPromptRepo.getMainPromptContent()

            // 6. 构筑时间调度调度中心提示词
            val systemPrompt = """
                $mainPrompt
                
                【CosmOS 时间流变调度中心 - 离线消息模拟系统】
                你现在正扮演 CosmOS 系统的“离线消息模拟调度中心”。
                由于用户执行了“时间跳过”操作，在这一虚拟时间段内，用户处于离线状态。你需要模拟并判定在此期间，现有的联系人角色们是否会因为各自的作息设定、人设背景以及与用户的最新聊天进展，而在此期间给用户发送离线未读消息。

                【跳过时间与玩家状态】：
                - 起始虚拟时间（跳过前时间）：$startTimeStr
                - 结束虚拟时间（跳过后的当前时间）：$endTimeStr
                - 玩家（用户）在这段时间内正在做什么：${if (userActivity.isBlank()) "日常活动/未明确说明" else userActivity}
                - 玩家真实姓名：$playerRealName

                【硬性限额约束】：
                - ⚠️ 在这整段跳过时间内，【每个角色最多被模拟发送 $timeSkipMaxMessages 条消息】！生成的任何角色消息条数绝对不能超过这一限制！

                【已建立会话的候选联系人列表及其设定（含最新聊天记录）】：
                -----------------------------------------
                $npcContextsBuilder
                -----------------------------------------

                【模拟判断核心准则（极其重要）】：
                1. **结合时间与作息规律**：请仔细判断 [起始时间, 结束时间] 这段期间是几点到几点（比如凌晨 1点 到 早上 8点）。根据每个角色的人设与日常作息设定，判断在此期间他们可能会在做什么，是否会发消息。
                   - 深夜或凌晨（如 23:00 - 06:00）：除非是夜猫子、突然遇到紧急状况、或者是极亲密关系的熬夜闲聊，绝大部分角色应该处于睡觉或静止状态，绝对不要乱发消息。
                   - 清晨（如 07:00 - 09:00）：可能会有一些生活作息规律或热情的角色起床后给玩家发“早安”、“约早饭”、“催起床”等。
                   - 工作日白天：忙碌设定的角色可能只在午休时间（12:00 - 13:30）或者下午茶时间发一两句，而闲散或摸鱼设定的角色则可能随时会发。
                2. **结合玩家活动**：如果玩家正在“睡觉”或“开会”，角色可能会在不知道的情况下发消息，但在没有得到回复后，可能表现出递进的特征（如：隔了一小时后发“你睡着啦？”、“那晚安哦~”）。如果角色本来就知道玩家在睡觉，则可能完全不会打扰。
                3. **结合最新聊天上下文**：如果最近的聊天历史中，角色和玩家刚聊完或正要约着做某事，请保持剧情连贯性。如果很久没聊，可能会是主动的日常问候。
                4. **非硬性发送**：这是一项基于日常逻辑的“拟真模拟”。请根据上述逻辑进行合理评估。有的角色会发多条（但绝对不能超过 $timeSkipMaxMessages 条，中间间隔几分钟或几小时，表明打字和时间推移），有的角色可能发 1 条，有的角色在这段时间内则【完全不会发消息】。如果都不发消息，模拟消息列表可以为空。
                
                【消息输出格式与要求】：
                1. 模拟消息的虚拟发送时间 `time` 必须严格在 `[$startTimeStr]` 和 `[$endTimeStr]` 的闭区间内！
                2. 同一个角色的多条消息，必须按照发送时间从小到大（升序）排序。
                3. 消息的 `type` 可为：
                   - "text"：纯文本聊天（必须极其符合对应角色人设口吻，简洁口语化，单条控制在50字内。指代用户必须使用第二人称“你”，绝对禁止使用“他/她”。**严禁夹带 any emoji、颜文字、表情符号或中括号/小括号的动作描写！**）。
                   - "image"：图片，`content` 填写该角色想发送的图片的文字画面描述。
                   - "red_packet"：红包，`content` 填写红包金额（必须是数字字符串，如 "15.00"），`extra` 填写祝福语。
                   - "transfer"：转账，`content` 填写转账金额。
                   - "location"：位置分享，`content` 填写分享的地理位置名称。

                【底层通信输出格式】：
                为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
                {
                  "simulated_messages": [
                    {
                      "character_id": "对应的角色 ID (NPC 的 character_id)",
                      "character_name": "角色名字",
                      "type": "text",
                      "time": "yyyy-MM-dd HH:mm:ss",
                      "content": "起好早啊，你起床了吗？"
                    },
                    {
                      "character_id": "对应的角色 ID (NPC 的 character_id)",
                      "character_name": "角色名字",
                      "type": "red_packet",
                      "time": "yyyy-MM-dd HH:mm:ss",
                      "content": "10.00",
                      "extra": "请你吃早饭！"
                    }
                  ]
                }
                
                特别注意：
                - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
                - 如果这期间没有任何人发消息，"simulated_messages" 数组填空数组即可。
            """.trimIndent()

            // 7. 获取当前激活的 AI 模型服务
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

            // 发起请求并获取响应文本
            val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")
            
            val responseText = if (isGeminiOfficial) {
                executeGeminiOfficialForSkip(baseUrl, modelName, apiKey, temperature, systemPrompt)
            } else {
                executeOpenAIForSkip(baseUrl, modelName, apiKey, temperature, systemPrompt, activeProfile.serviceType, activeProfile.thinkingLevel)
            }

            // ── 拦截并保存 AI 通讯日志 ──────────────────────────
            try {
                val logRepo = AiLogRepository(context)
                logRepo.saveLog(
                    characterName = "时间跳过模拟器",
                    modelName = modelName,
                    userInput = "跳过区间: $startTimeStr -> $endTimeStr, 用户活动: ${if (userActivity.isBlank()) "无" else userActivity}",
                    aiResponse = responseText,
                    prompt = systemPrompt
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 8. 解析返回的离线消息 JSON 并持久化
            val cleanJson = cleanJsonResponse(responseText)
            val jsonObj = JSONObject(cleanJson)
            val simMessagesArray = jsonObj.optJSONArray("simulated_messages") ?: JSONArray()
            
            var savedCount = 0
            val sdfParser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for (i in 0 until simMessagesArray.length()) {
                val msgObj = simMessagesArray.getJSONObject(i)
                val charId = msgObj.optString("character_id", "")
                val type = msgObj.optString("type", "text")
                val timeStr = msgObj.optString("time", "")
                val content = msgObj.optString("content", "")
                val extra = if (msgObj.has("extra") && !msgObj.isNull("extra")) msgObj.getString("extra") else null

                if (charId.isBlank() || content.isBlank() || timeStr.isBlank()) continue

                // 从刚才建立好的候选者映射中找对应的联系人
                val match = candidateNpcs.firstOrNull { it.second.id == charId } ?: continue
                val contact = match.first

                // 解析出发送时间的毫秒戳
                val msgTimeMillis = try {
                    sdfParser.parse(timeStr)?.time ?: (startTimeMillis + (endTimeMillis - startTimeMillis) / 2)
                } catch (e: Exception) {
                    startTimeMillis + (endTimeMillis - startTimeMillis) / 2
                }

                // 强制将时间戳纠正至 [startTimeMillis, endTimeMillis] 范围内
                val boundedTimeMillis = msgTimeMillis.coerceIn(startTimeMillis, endTimeMillis)

                // 构建并保存 ChatMessage
                val chatMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = contact.id, // senderId 为联系人 ID，表示角色发出的消息
                    content = content,
                    timestamp = boundedTimeMillis,
                    type = type,
                    extra = extra
                )
                chatRepo.saveMessage(contact.id, chatMessage)
                savedCount++
            }

            // 9. 成功后推进虚拟时间为跳过目标结束时间
            VirtualTimeManager.updateTime(endTimeMillis)

            TimeSkipResult(true, savedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            TimeSkipResult(false, 0, e.message ?: "模拟离线消息生成失败")
        }
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

    private fun executeGeminiOfficialForSkip(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String
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

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().apply {
                            put("text", systemPrompt)
                        }
                    ))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
                put("maxOutputTokens", 2048)
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

    private fun executeOpenAIForSkip(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
        serviceType: AiServiceType,
        thinkingLevel: String
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.connectTimeout = 45000
        conn.readTimeout = 45000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "根据起始虚拟时间与结束虚拟时间，结合各角色作息，生成模拟出来的离线消息 JSON。")
            })
        }

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
            put("max_tokens", 2048)
            if (thinkingLevel == "off") {
                if (serviceType == AiServiceType.DEEP_SEEK || modelName.contains("deepseek", ignoreCase = true)) {
                    put("thinking", JSONObject().apply {
                        put("type", "disabled")
                    })
                }
            } else if (thinkingLevel != "default") {
                put("reasoning_effort", thinkingLevel)
                if (serviceType == AiServiceType.DEEP_SEEK || modelName.contains("deepseek", ignoreCase = true)) {
                    put("thinking", JSONObject().apply {
                        put("type", "enabled")
                    })
                }
            }
            
            if (serviceType == AiServiceType.OPEN_AI) {
                val replySchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("character_id", JSONObject().apply { put("type", "string") })
                        put("character_name", JSONObject().apply { put("type", "string") })
                        put("type", JSONObject().apply { put("type", "string") })
                        put("time", JSONObject().apply { put("type", "string") })
                        put("content", JSONObject().apply { put("type", "string") })
                        put("extra", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply {
                        put("character_id")
                        put("character_name")
                        put("type")
                        put("time")
                        put("content")
                    })
                    put("additionalProperties", false)
                }

                val openAiSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("simulated_messages", JSONObject().apply {
                            put("type", "array")
                            put("items", replySchema)
                        })
                    })
                    put("required", JSONArray().apply {
                        put("simulated_messages")
                    })
                    put("additionalProperties", false)
                }

                put("response_format", JSONObject().apply {
                    put("type", "json_schema")
                    put("json_schema", JSONObject().apply {
                        put("name", "time_skip_response")
                        put("strict", true)
                        put("schema", openAiSchema)
                    })
                })
            } else {
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
