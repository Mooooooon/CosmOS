package com.moonlib.cosmos.data.diary

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * AI 日记生成引擎
 *
 * 职责单一：负责组织角色设定、往期日记金字塔式上下文、写作人称及状态卡配置，
 * 调用当前激活的 AI 模型并解析返回的结构化 JSON（含正文、摘要与角色状态更新），
 * 同步更新全局角色实时状态卡，并返回完整 DiaryEntry。
 */
object DiaryEngine {

    /**
     * 根据玩家输入与参与角色，通过 AI 创作生成日记条目。
     */
    suspend fun generateDiary(
        context: Context,
        playerInput: String,
        involvedCharacterIds: List<String>,
        diariesContextOverride: List<DiaryEntry>? = null
    ): DiaryEntry = withContext(Dispatchers.IO) {
        val diaryRepo = DiaryRepository(context)
        val profileRepo = CharacterProfileRepository(context)
        val configRepo = AiConfigRepository(context)
        val interactionSettingsRepo = InteractionSettingsRepository(context)
        val systemPromptRepo = SystemPromptRepository(context)

        // 1. 基础校验与配置读取
        if (involvedCharacterIds.isEmpty()) {
            throw Exception("请选择至少一个参与本篇日记的角色。")
        }

        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("未检测到激活的 AI 模型。请前往【系统设置】配置模型服务。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查。")
        }

        // 2. 获取人设与玩家信息
        val characterProfiles = profileRepo.getProfiles().filter { involvedCharacterIds.contains(it.id) }
        if (characterProfiles.isEmpty()) {
            throw Exception("所选的参与角色设定不存在。")
        }

        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val chatRepo = com.moonlib.cosmos.data.chat.ChatRepository(context)
        val userNickname = chatRepo.getUserNickname()
        val playerRealName = playerProfile?.name ?: userNickname

        // 3. 组装 AI System Prompt 核心基底
        val mainPrompt = systemPromptRepo.getMainPromptContent()
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 人称指示词
        val perspective = diaryRepo.getPerspective()
        val perspectiveInstruction = if (perspective == "first") {
            "本次日记创作必须采用【第一视角】编写，即以**用户/玩家自己（真实姓名为 ${playerRealName}，在日记叙事中自称为“我”）**的日记主视角和叙述主体，来记录本篇日记。请生动描述“我”（玩家）今天与参与本次经历的角色们（${characterProfiles.joinToString("、") { it.name }}）发生的相处故事、细节互动，以及“我”对她们的内心真实感悟。"
        } else {
            "本次日记创作必须采用【第三视角】编写，采用旁观者或上帝视角，以剧情客观发展的口吻来叙述和记录玩家（真实姓名 ${playerRealName}）与参与角色们（${characterProfiles.joinToString("、") { it.name }}）之间发生的故事经历与互动细节。"
        }

        // 组装参与人设详细描述
        val charProfilesStr = characterProfiles.joinToString("\n\n") { char ->
            val rawCharPrompt = char.prompt
            val processedCharPrompt = rawCharPrompt
                .replace("{{char}}", char.name)
                .replace("{{user}}", playerRealName)
            "角色【${char.name}】的性格外貌设定:\n--------------------------------\n$processedCharPrompt\n--------------------------------"
        }

        val playerPrompt = playerProfile?.prompt ?: "普通用户，无更多公开身份设定。"
        val processedPlayerPrompt = playerPrompt
            .replace("{{char}}", characterProfiles.firstOrNull()?.name ?: "角色")
            .replace("{{user}}", playerRealName)

        // 状态卡组装（共用词条与角色实时状态）
        val diaryStatusCardEnabled = interactionSettingsRepo.isDiaryStatusCardEnabled()
        val statusKeys = interactionSettingsRepo.getStatusKeys()
        val charStatusPrompt = if (diaryStatusCardEnabled && statusKeys.isNotEmpty()) {
            val charStatusBuilder = StringBuilder()
            charStatusBuilder.append("\n【当前参与角色的实时状态快照（重要，请参考或在此基础上更新）】:\n")
            for (char in characterProfiles) {
                val charStatusMap = interactionSettingsRepo.getCharacterStatus(char.id)
                val statusBulletPoints = statusKeys.joinToString(", ") { key ->
                    val currentVal = charStatusMap[key.name] ?: "未知"
                    "「${key.name}」: $currentVal"
                }
                charStatusBuilder.append("- 角色【${char.name}】的当前状态为: $statusBulletPoints\n")
            }
            charStatusBuilder.append("\n【状态更新输出准则】:\n")
            charStatusBuilder.append("由于当前日记的状态卡已开启。你作为扮演角色们的上帝/内心视角，必须协同维护或更新每个参与角色的实时状态。如果本次经历让角色的身体姿势、动作神态或衣服配饰发生改变，你必须在输出 JSON 的外层添加并输出 \"status\" 对象，里面包含发生改变的角色姓名，及其更新了的状态值。对于没有改变的词条，将其值设为 null，或者直接不输出。\n")
            charStatusBuilder.append("要求：\n")
            charStatusBuilder.append("1. 状态词条值中绝对不能带任何中文小括号『（』『）』或英文小括号『(』『)』！\n")
            charStatusBuilder.append("2. 状态值如果涉及指代玩家（即用户），必须使用第二人称“你”（如“靠在你肩上”、“穿着你的衣服”），绝对禁止使用第三人称“他/她”！\n")
            charStatusBuilder.toString()
        } else {
            ""
        }

        // 4. 全局时序混合上下文记忆流组装 (包含线上聊天、线下实体互动、以及金字塔式日记)
        data class DiaryMergedMessage(val sender: String, val content: String, val timestamp: Long)

        // 4.1 拉取所有相关的聊天历史
        val allChatMsgs = mutableListOf<com.moonlib.cosmos.data.chat.ChatMessage>()
        for (charId in involvedCharacterIds) {
            val contact = chatRepo.getContacts().firstOrNull { it.characterId == charId } ?: continue
            allChatMsgs.addAll(chatRepo.getMessages(contact.id))
        }
        val formattedChatMsgs = allChatMsgs.map { msg ->
            val formattedContent = when (msg.type) {
                "image" -> "[发送了图片：${msg.content}]"
                "video" -> "[发送了视频：${msg.content}]"
                "red_packet" -> "[发送了红包：${msg.content}元，留言：${msg.extra ?: "恭喜发财，大吉大利"}]"
                "transfer" -> "[发送了转账：${msg.content}元]"
                "location" -> "[发送了位置：${msg.content}]"
                else -> msg.content
            }
            val senderName = if (msg.senderId == "user") playerRealName else (characterProfiles.firstOrNull { it.id == msg.senderId }?.name ?: "角色")
            DiaryMergedMessage(senderName, "[线上聊天] $formattedContent", msg.timestamp)
        }

        // 4.2 拉取所有相关的线下互动历史
        val interactionRepo = com.moonlib.cosmos.data.interaction.InteractionRepository(context)
        val allInteractionMsgs = mutableListOf<com.moonlib.cosmos.data.interaction.InteractionMessage>()
        for (charId in involvedCharacterIds) {
            allInteractionMsgs.addAll(interactionRepo.getMessages(charId))
        }
        val formattedInteractionMsgs = allInteractionMsgs.map { msg ->
            val senderName = if (msg.senderId == "user") playerRealName else (characterProfiles.firstOrNull { it.id == msg.senderId }?.name ?: "角色")
            DiaryMergedMessage(senderName, "[线下互动] ${msg.content}", msg.timestamp)
        }

        // 4.3 按照金字塔规则提取过往日记 (上一篇完整正文，更早篇摘要)
        val allDiaries = (diariesContextOverride ?: diaryRepo.getDiaries()).sortedBy { it.timestamp }
        val diariesHistoryMsgs = mutableListOf<DiaryMergedMessage>()
        val diariesSize = allDiaries.size
        for (i in 0 until diariesSize) {
            val diary = allDiaries[i]
            val diaryContent = if (i == diariesSize - 1) {
                "[剧情日记 - 完整正文] ${diary.content}"
            } else {
                "[剧情日记 - 剧情摘要] ${diary.summary}"
            }
            diariesHistoryMsgs.add(
                DiaryMergedMessage(
                    sender = playerRealName,
                    content = diaryContent,
                    timestamp = diary.timestamp
                )
            )
        }

        // 4.4 合并全部日常足迹与日记历史并依物理时间戳升序重排
        val finalMergedHistory = (
            formattedChatMsgs +
            formattedInteractionMsgs +
            diariesHistoryMsgs
        ).sortedBy { it.timestamp }

        // 获取最近 80 条上下文切片以防止 token 溢出，并保障记忆关联的时效性
        val recentHistorySlice = finalMergedHistory.takeLast(80)

        val diariesHistoryPrompt = if (recentHistorySlice.isNotEmpty()) {
            val historyStr = recentHistorySlice.joinToString("\n") { msg ->
                "${msg.sender}: ${msg.content}"
            }
            """
            
            【全局时序混合上下文记忆流（包含线上聊天、线下互动与过往剧情日记，极其重要）】:
            以下是玩家和参与角色们在最近一段时间内发生的所有线上聊天、线下面对面实体互动以及在写日记时记录的历史剧情。它们已按照物理发生时间戳从小到大（升序）混合排列在下方。请务必作为前情提要，了解最近故事发生了什么，以完美继承和推进剧情脉络：
            --------------------------------------------------
            $historyStr
            --------------------------------------------------
            """
        } else {
            "\n【全局时序混合上下文记忆流】: 暂无过往的线上聊天、线下互动或日记记录，这是你们的第一次故事记录。"
        }

        val systemPrompt = """
            $mainPrompt
            
            你现在是拥有全知与角色内心叙事视角的日记撰写大师。
            请根据用户输入的事件引子，创作一篇日记。
            
            【日记创作核心原则】：
            1. $perspectiveInstruction
            2. 日记内容应当情感充沛、细节细腻、逻辑自洽，像真实的日记记录一样，避免假大空的套话。
            3. 字数要求：日记正文建议控制在 250 至 500 字之间。
            
            【参与本篇日记的角色设定如下】：
            $charProfilesStr
            
            【玩家设定如下】：
            --------------------------------
            $processedPlayerPrompt
            --------------------------------
            
            【当前虚拟世界的时间】是：$currentVirtualTimeStr
            $diariesHistoryPrompt
            $charStatusPrompt
            
            【底层输出通信格式要求 (极其严格)】：
            你必须以 JSON 格式输出，不要包含 markdown 块或任何废话，你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "content": "你所创作的高质量日记完整正文，绝对严禁在正文中使用 any emoji 或颜文字符号，保持纯文字叙事。",
              "summary": "对本次事件精简的一句话日记摘要 (建议20到40字)，供后续检索和上下文拼接使用。"${if (diaryStatusCardEnabled && statusKeys.isNotEmpty()) ",\n              \"status\": {\n                \"角色A的名字\": {\n                  \"词条名1\": \"例如：伏在你的手边小憩\",\n                  \"词条名2\": null\n                }\n              }" else ""}
            }
            
            极其重要的要求：
            - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
        """.trimIndent()

        val userPrompt = """
            【本次日记的剧情起因/事件引子（基于此进行发散创作）】：
            $playerInput
            
            请创作并直接输出这篇日记对应的 JSON 结构。
        """.trimIndent()

        // 4. 调用 AI 接口获取响应
        val isGeminiOfficial = activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")
        val responseText = if (isGeminiOfficial) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, userPrompt)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, userPrompt, activeProfile.serviceType, characterProfiles, statusKeys, diaryStatusCardEnabled)
        }

        // 5. 保存 AI 日志，便于在设置应用中查看
        try {
            val logRepo = com.moonlib.cosmos.data.settings.AiLogRepository(context)
            logRepo.saveLog(
                characterName = "日记生成 (共 ${characterProfiles.size} 人)",
                modelName = modelName,
                userInput = playerInput,
                aiResponse = responseText,
                prompt = "=== System Prompt ===\n$systemPrompt\n\n=== User Prompt ===\n$userPrompt"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. 解析大模型返回的 JSON
        val cleanJson = cleanJsonResponse(responseText)
        val jsonObj = JSONObject(cleanJson)
        val content = jsonObj.getString("content").trim()
        val summary = jsonObj.getString("summary").trim()

        // 提取 status 并更新全局角色实时状态
        val statusMap = mutableMapOf<String, Map<String, String>>()
        if (diaryStatusCardEnabled && statusKeys.isNotEmpty() && jsonObj.has("status") && !jsonObj.isNull("status")) {
            val statusObj = jsonObj.optJSONObject("status")
            if (statusObj != null) {
                val charNamesKeys = statusObj.keys()
                while (charNamesKeys.hasNext()) {
                    val charName = charNamesKeys.next()
                    val charStatusObj = statusObj.optJSONObject(charName)
                    if (charStatusObj != null) {
                        val singleCharStatus = mutableMapOf<String, String>()
                        val innerKeys = charStatusObj.keys()
                        while (innerKeys.hasNext()) {
                            val key = innerKeys.next()
                            if (!charStatusObj.isNull(key)) {
                                val value = charStatusObj.getString(key)
                                if (value.isNotBlank() && value != "null") {
                                    singleCharStatus[key] = cleanStatusValue(value)
                                }
                            }
                        }
                        if (singleCharStatus.isNotEmpty()) {
                            // 查找 ID
                            val charId = characterProfiles.firstOrNull { it.name == charName }?.id
                            if (charId != null) {
                                statusMap[charId] = singleCharStatus
                                // 更新全局状态卡，使数据能够顺滑在“日记”与“互动”APP间传递！
                                interactionSettingsRepo.updateCharacterStatus(charId, singleCharStatus)
                            }
                        }
                    }
                }
            }
        }

        // 组装最终的 DiaryEntry
        DiaryEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            virtualTime = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss"),
            playerInput = playerInput,
            content = content,
            summary = summary,
            involvedCharacterIds = involvedCharacterIds,
            statusMap = statusMap
        )
    }

    // ─── 接口通信与数据清洗辅助方法 ────────────────────────────────

    private fun executeOpenAI(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
        userPrompt: String,
        serviceType: AiServiceType,
        characterProfiles: List<com.moonlib.cosmos.data.profile.CharacterProfile>,
        statusKeys: List<com.moonlib.cosmos.data.interaction.StatusKey>,
        statusCardEnabled: Boolean
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

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
            put("max_tokens", 2048)

            if (serviceType == AiServiceType.OPEN_AI) {
                // OpenAI 官方 Schema 强约束
                val openAiSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("content", JSONObject().apply { put("type", "string") })
                        put("summary", JSONObject().apply { put("type", "string") })
                        if (statusCardEnabled && statusKeys.isNotEmpty()) {
                            val statusProps = JSONObject()
                            val statusRequired = JSONArray()
                            for (char in characterProfiles) {
                                val charProps = JSONObject()
                                val charRequired = JSONArray()
                                for (key in statusKeys) {
                                    charProps.put(key.name, JSONObject().apply {
                                        put("type", JSONArray().apply { put("string"); put("null") })
                                    })
                                    charRequired.put(key.name)
                                }
                                statusProps.put(char.name, JSONObject().apply {
                                    put("type", "object")
                                    put("properties", charProps)
                                    put("required", charRequired)
                                    put("additionalProperties", false)
                                })
                                statusRequired.put(char.name)
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
                        put("content")
                        put("summary")
                        if (statusCardEnabled && statusKeys.isNotEmpty()) {
                            put("status")
                        }
                    })
                    put("additionalProperties", false)
                }

                put("response_format", JSONObject().apply {
                    put("type", "json_schema")
                    put("json_schema", JSONObject().apply {
                        put("name", "diary_generation_response")
                        put("strict", true)
                        put("schema", openAiSchema)
                    })
                })
            } else {
                // 其他模型采用 JSON Object Mode
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

    private fun executeGeminiOfficial(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
        userPrompt: String
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
            val partsArray = JSONArray().apply {
                put(JSONObject().apply { put("text", "System Instructions:\n$systemPrompt\n\nUser Input:\n$userPrompt") })
            }
            val contentsObj = JSONObject().apply {
                put("parts", partsArray)
            }
            put("contents", JSONArray().put(contentsObj))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
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
            throw Exception("AI 接口报错 HTTP $responseCode: ${errorText.take(120)}")
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

    private fun cleanStatusValue(value: String): String {
        return value.replace(Regex("[()（）]"), "").trim()
    }
}
