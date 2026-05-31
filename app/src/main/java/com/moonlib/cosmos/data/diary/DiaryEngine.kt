package com.moonlib.cosmos.data.diary

import android.content.Context
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiAuthorizationHeader
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiReasoningRequestOptions
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiVertexConfig
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
 * AI 剧情推进引擎（日记模块）
 *
 * 职责单一：负责组织角色设定、往期日记金字塔式上下文、写作人称及状态卡配置，
 * 以"剧情场景推进"（而非事后回顾）的模式调用 AI 生成当下发生的场景正文，
 * 解析返回的结构化 JSON（含正文、摘要、建议时间与角色状态更新），
 * 自动推进全局虚拟时间轴，同步更新角色实时状态卡，并返回完整 DiaryEntry。
 */
object DiaryEngine {

    /**
     * 根据玩家输入与参与角色，通过 AI 推进剧情并生成日记条目。
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
            throw Exception("请选择至少一个参与本篇剧情的角色。")
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
        // 记录剧情开始时的虚拟时间（用于 DiaryEntry.virtualTime 及告知 AI 起始时间）
        val startVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 人称指示词
        val perspective = diaryRepo.getPerspective()
        val perspectiveInstruction = if (perspective == "first") {
            """本次剧情创作必须采用【第一视角】编写，即以**用户/玩家自己（真实姓名为 ${playerRealName}，在叙事中自称为"我"）**的视角作为叙述主体，生动描述"我"（玩家）与参与本次经历的角色们（${characterProfiles.joinToString("、") { it.name }}）正在发生的相处过程、细节互动，以及"我"对她们的内心真实感受。"""
        } else {
            "本次剧情创作必须采用【第三视角】编写，采用旁观者或上帝视角，以客观发展的口吻来叙述和记录玩家（真实姓名 ${playerRealName}）与参与角色们（${characterProfiles.joinToString("、") { it.name }}）之间正在发生的故事经历与互动细节。"
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
            charStatusBuilder.append("由于当前剧情的状态卡已开启。你作为扮演角色们的上帝/内心视角，必须协同维护或更新每个参与角色的实时状态。如果本次经历让角色的身体姿势、动作神态或衣服配饰发生改变，你必须在输出 JSON 的外层添加并输出 \"status\" 对象，里面包含发生改变的角色姓名，及其更新了的状态值。对于没有改变的词条，将其值设为 null，或者直接不输出。\n")
            charStatusBuilder.append("要求：\n")
            charStatusBuilder.append("1. 状态词条值中绝对不能带任何中文小括号『（』『）』或英文小括号『(』『)』！\n")
            charStatusBuilder.append("2. 状态值如果涉及指代玩家（即用户），必须使用第二人称\"你\"（如\"靠在你肩上\"、\"穿着你的衣服\"），绝对禁止使用第三人称\"他/她\"！\n")
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
                "[剧情场景 - 完整正文] ${diary.content}"
            } else {
                "[剧情场景 - 摘要] ${diary.summary}"
            }
            val parsedTimestamp = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                sdf.parse(diary.virtualTime)?.time ?: diary.timestamp
            } catch (e: Exception) {
                diary.timestamp
            }
            diariesHistoryMsgs.add(
                DiaryMergedMessage(
                    sender = playerRealName,
                    content = diaryContent,
                    timestamp = parsedTimestamp
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
            
            【全局时序混合上下文记忆流（包含线上聊天、线下互动与过往剧情场景，极其重要）】:
            以下是玩家和参与角色们在最近一段时间内发生的所有线上聊天、线下面对面实体互动以及过往剧情场景记录。它们已按照物理发生时间戳从小到大（升序）混合排列在下方。请务必作为前情提要，了解最近故事发生了什么，以完美继承和推进剧情脉络：
            --------------------------------------------------
            $historyStr
            --------------------------------------------------
            """
        } else {
            "\n【全局时序混合上下文记忆流】: 暂无过往的线上聊天、线下互动或剧情记录，这是你们的第一次故事。"
        }

        // ─── 核心 System Prompt：剧情场景推进模式 ───────────────────
        val systemPrompt = """
            $mainPrompt
            
            你现在是一位负责推进剧情的叙事大师，掌控着这个虚拟世界所有角色的行动与感受。
            玩家给出了一个剧情引子，你需要将其扩展为一段生动的线下场景剧情，就像写小说一样。
            
            【剧情推进核心原则】：
            1. $perspectiveInstruction
            2. 你创作的是【正在发生的当下场景】，不是事后回顾或日记总结。请用现在进行时的笔触，生动地描写这段剧情的过程、细节、对话与情感。
            3. 字数要求：剧情正文建议控制在 300 至 600 字之间，情节饱满、细节细腻。
            4. 剧情必须有自然的节奏感——有起因（承接玩家引子）、经过（场景展开）、高光时刻（情感或事件的小高潮）、以及一个自然收尾。
            5. 禁止假大空的套话和堆砌词语，一切描写务必具体、可感。
            
            【参与本次剧情的角色设定如下】：
            $charProfilesStr
            
            【玩家设定如下】：
            --------------------------------
            $processedPlayerPrompt
            --------------------------------
            
            【当前虚拟世界的时间】是：$currentVirtualTimeStr
            （这是本次剧情的【起始时间】，你需要根据剧情内容的自然发展，在输出中给出剧情结束时的合理目标时间）
            $diariesHistoryPrompt
            $charStatusPrompt
            
            【底层输出通信格式要求 (极其严格)】：
            你必须以 JSON 格式输出，不要包含 markdown 块或任何废话，你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "content": "你创作的高质量剧情场景完整正文，绝对严禁在正文中使用 any emoji 或颜文字符号，保持纯文字叙事。",
              "summary": "对本次剧情精简的一句话摘要（建议20到40字），供后续检索和上下文拼接使用。",
              "nextTime": "剧情自然结束时刻的虚拟时间，格式严格为 yyyy-MM-dd HH:mm（如 2024-06-15 19:30），必须晚于或等于当前起始时间，根据剧情内容合理推断（如下午去游乐场游玩，则推进到傍晚 18:00 左右）。"${if (diaryStatusCardEnabled && statusKeys.isNotEmpty()) ",\n              \"status\": {\n                \"角色A的名字\": {\n                  \"词条名1\": \"例如：伏在你的手边小憩\",\n                  \"词条名2\": null\n                }\n              }" else ""}
            }
            
            极其重要的要求：
            - 必须只返回纯 JSON，不能包裹在 ```json ... ``` 块中，也不要说任何废话。
            - nextTime 字段必须存在且格式正确（yyyy-MM-dd HH:mm），这对虚拟世界时间轴推进至关重要。
        """.trimIndent()

        val userPrompt = """
            【本次剧情的起因/引子（请据此展开创作）】：
            $playerInput
            
            请创作这段剧情并直接输出对应的 JSON 结构。
        """.trimIndent()

        // 5. 调用 AI 接口获取响应
        val isNativeGenerateContent = activeProfile.serviceType == AiServiceType.VERTEX ||
            (activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com"))
        val responseText = if (isNativeGenerateContent) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, userPrompt, activeProfile.serviceType, activeProfile.vertexRegion)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, userPrompt, activeProfile.serviceType, characterProfiles, statusKeys, diaryStatusCardEnabled, activeProfile.thinkingLevel)
        }

        // 6. 保存 AI 日志，便于在设置应用中查看
        try {
            val logRepo = com.moonlib.cosmos.data.settings.AiLogRepository(context)
            logRepo.saveLog(
                characterName = "剧情推进 (共 ${characterProfiles.size} 人)",
                modelName = modelName,
                userInput = playerInput,
                aiResponse = responseText,
                prompt = "=== System Prompt ===\n$systemPrompt\n\n=== User Prompt ===\n$userPrompt"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 7. 解析大模型返回的 JSON
        val cleanJson = cleanJsonResponse(responseText)
        val jsonObj = JSONObject(cleanJson)
        val content = jsonObj.getString("content").trim()
        val summary = jsonObj.getString("summary").trim()

        // 解析 AI 给出的剧情结束目标时间，并推进虚拟时间轴
        val nextVirtualTimeStr = jsonObj.optString("nextTime", "").trim()
        if (nextVirtualTimeStr.isNotBlank()) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val parsed = sdf.parse(nextVirtualTimeStr)
                if (parsed != null) {
                    // updateTime 内部已保证只允许向前推进，不会倒退
                    VirtualTimeManager.updateTime(parsed.time)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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
                            val charId = characterProfiles.firstOrNull { it.name == charName }?.id
                            if (charId != null) {
                                statusMap[charId] = singleCharStatus
                                // 更新全局状态卡，使数据能够顺滑在"日记"与"互动"APP间传递
                                interactionSettingsRepo.updateCharacterStatus(charId, singleCharStatus)
                            }
                        }
                    }
                }
            }
        }

        // 8. 组装最终 DiaryEntry
        DiaryEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            virtualTime = startVirtualTimeStr,
            nextVirtualTime = nextVirtualTimeStr,
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
        statusCardEnabled: Boolean,
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
            AiReasoningRequestOptions.applyTo(this, serviceType, modelName, thinkingLevel)

            if (serviceType == AiServiceType.OPEN_AI) {
                // OpenAI 官方 Schema 强约束，加入 nextTime 必填字段
                val openAiSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("content", JSONObject().apply { put("type", "string") })
                        put("summary", JSONObject().apply { put("type", "string") })
                        put("nextTime", JSONObject().apply { put("type", "string") })
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
                        put("nextTime")
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
        userPrompt: String,
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

        val requestJson = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().apply { put("text", "System Instructions:\n$systemPrompt\n\nUser Input:\n$userPrompt") })
            }
            val contentsObj = JSONObject().apply {
                put("role", "user")
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
