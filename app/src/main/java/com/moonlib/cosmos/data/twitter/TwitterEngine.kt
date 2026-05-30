package com.moonlib.cosmos.data.twitter

import android.content.Context
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiAuthorizationHeader
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiReasoningRequestOptions
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiVertexConfig
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * 推特 AI 回复与事件模拟引擎
 * 
 * 职责单一：负责推特的 NPC 智能盖楼回复决策、虚拟时间推演主动发推逻辑，以及底层的 AI 数据交互与 JSON 解析。
 */
object TwitterEngine {

    /**
     * 当用户发推或回复后，异步触发被关注的角色进行盖楼评论
     */
    fun triggerNpcRepliesAsync(context: Context, tweetId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkAndGenerateNpcReplies(context, tweetId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 智能判定已关注角色，并生成针对某条推文的盖楼回复列表
     */
    suspend fun checkAndGenerateNpcReplies(context: Context, tweetId: String): List<Tweet> = withContext(Dispatchers.IO) {
        val twitterRepo = TwitterRepository(context)
        val targetTweet = twitterRepo.getTweet(tweetId) ?: return@withContext emptyList()
        
        // 1. 获取所有已关注的角色推特档案
        val followedProfiles = twitterRepo.getFollowedProfiles()
        if (followedProfiles.isEmpty()) return@withContext emptyList()

        val profileRepo = CharacterProfileRepository(context)
        val systemProfiles = profileRepo.getProfiles()
        
        // 2. 拼接当前推文回复树的完整历史
        val threadHistory = mutableListOf<Tweet>()
        var current: Tweet? = targetTweet
        while (current != null) {
            threadHistory.add(0, current) // 最老的在前
            current = current.parentId?.let { twitterRepo.getTweet(it) }
        }

        val threadTextBuilder = StringBuilder()
        for (t in threadHistory) {
            val authorProf = twitterRepo.getProfile(t.authorId)
            val authorName = authorProf?.nickname ?: t.authorId
            val authorHandle = authorProf?.username ?: t.authorId
            val replyPart = if (t.replyToUsername != null) " 回复 @${t.replyToUsername}" else ""
            threadTextBuilder.append("- [@${authorHandle} ($authorName)$replyPart]: ${t.content}\n")
        }

        // 3. 构造候选角色的详细性格作息设定与通用上下文（线上聊天/实体互动/日记/推特合并的全局记忆）
        val charactersInfo = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
        val maxContextSize = com.moonlib.cosmos.data.settings.AiSettingsRepository(context).getMaxContextSize().coerceAtMost(30)
        
        for (fProf in followedProfiles) {
            val systemProf = systemProfiles.firstOrNull { it.id == fProf.characterId } ?: continue
            val promptProcessed = systemProf.prompt
                .replace("{{char}}", fProf.nickname)
                .replace("{{user}}", "玩家")
                
            // 获取该 NPC 的全局合并通用上下文记忆
            val recentMerged = ConversationContextBuilder.buildForCharacter(context, systemProf, maxContextSize)
            val unifiedMemoryText = if (recentMerged.isEmpty()) {
                "（当前暂无与玩家的共同记忆与沟通历史）"
            } else {
                recentMerged.joinToString("\n") { msg ->
                    val senderName = if (msg.senderId == "user") "玩家" else fProf.nickname
                    "- [时间: ${sdf.format(Date(msg.timestamp))}] $senderName 的${msg.prefix}: ${msg.content}"
                }
            }

            charactersInfo.append("角色 ID (character_id): ${fProf.characterId}\n")
            charactersInfo.append("推特名字: ${fProf.nickname}\n")
            charactersInfo.append("推特用户名: @${fProf.username}\n")
            charactersInfo.append("个人简介: ${fProf.bio}\n")
            charactersInfo.append("【人设性格作息提示词】：\n$promptProcessed\n")
            charactersInfo.append("【该角色拥有的最新通用融合记忆（含线上私聊、线下互动、日记及推特动态）】：\n$unifiedMemoryText\n")
            charactersInfo.append("=========================================\n\n")
        }

        // 4. 获取全局系统提示词基底与 AI 设置
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 5. 组装专为推特互动的 System Prompt
        val systemPrompt = """
            $mainPrompt
            
            【CosmOS 虚拟手机推特（Twitter）盖楼评论系统】
            你现在正扮演 CosmOS 系统的“推特 AI 仿真互动引擎”。
            当玩家发送新推文，或者在已有评论区中发表回复后，你需要根据被关注角色的性格特征、彼此关系以及当时的时间，判定哪些人会刷到这条推文并会回复它。她们不仅可以回复原推文，还可能针对彼此的回复进行有趣的“盖楼套娃式互动”。
            
            【当前虚拟世界的时间】：$currentVirtualTimeStr
            
            【已关注的候选角色列表及其人设设定】：
            -----------------------------------------
            $charactersInfo
            -----------------------------------------
            
            【当前推文对话树历史（升序）】：
            -----------------------------------------
            $threadTextBuilder
            -----------------------------------------
            
            【盖楼决策与回复要求（极其重要）】：
            1. **拟真盖楼互动**：根据角色性格、作息以及彼此的关系，生成 0 到 3 条回复。有些角色性格热情，可能立刻评论；有的角色则可能互怼；如果没人合适，也可以返回空回复列表。
            2. **文字规范**：消息内容必须控制在 1 到 2 句话内（30字以内），严禁任何 emoji、颜文字或小括号内的动作描写！指代用户必须用第二人称“你”，绝对禁止使用“他”或“她”！
            3. **层级关系**：回复的 `parent_id` 可以是当前叶子结点推文的 ID （即 `"$tweetId"`），也可以是本组回复中前面那条回复的临时 ID，从而实现“角色互相回复彼此的评论”。
            4. **时间偏移**：每条回复指定一个 `time_offset_seconds`（在 10 到 120 秒之间，逐渐递增），用来代表真实用户刷推特、打字和发送的时间间隔。
            
            【底层通信输出格式】：
            为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块或额外的解释文本。你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "replies": [
                {
                  "character_id": "回复角色的 character_id",
                  "reply_to_username": "正在回复的那个人的推特用户名（不含@，例如 alice_wonderland）",
                  "content": "这景色真美，我也想去！",
                  "parent_id": "直接被回复的推文ID（原贴填 $tweetId，如果回复本组里另一个角色的评论，可填其在 replies 中的 index，例如: 'reply_index_0'）",
                  "time_offset_seconds": 15
                }
              ]
            }
        """.trimIndent()

        // 6. 发起 AI 请求并获取回复
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("未检测到激活的 AI 模型。请在【设置】中配置。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整。")
        }

        val isNativeGenerateContent = activeProfile.serviceType == AiServiceType.VERTEX ||
                (activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com"))

        val responseText = if (isNativeGenerateContent) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, activeProfile.serviceType, activeProfile.vertexRegion)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, activeProfile.serviceType, activeProfile.thinkingLevel)
        }

        // 保存通讯日志
        try {
            val logRepo = com.moonlib.cosmos.data.settings.AiLogRepository(context)
            logRepo.saveLog(
                characterName = "推特AI评论引擎",
                modelName = modelName,
                userInput = "目标推文: ${targetTweet.content}",
                aiResponse = responseText,
                prompt = systemPrompt
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 7. 解析返回的盖楼评论并保存
        val cleanJson = cleanJsonResponse(responseText)
        val jsonObj = JSONObject(cleanJson)
        val repliesArray = jsonObj.optJSONArray("replies") ?: JSONArray()
        
        val savedReplies = mutableListOf<Tweet>()
        val indexToIdMap = mutableMapOf<String, String>() // 用于映射 'reply_index_X' 到真正的 UUID

        var lastTime = targetTweet.timestamp

        for (i in 0 until repliesArray.length()) {
            val repObj = repliesArray.getJSONObject(i)
            val charId = repObj.optString("character_id", "")
            val content = repObj.optString("content", "")
            val replyToUser = if (repObj.isNull("reply_to_username")) null else repObj.getString("reply_to_username")
            val parentIdStr = repObj.optString("parent_id", tweetId)
            val offsetSec = repObj.optInt("time_offset_seconds", 15)

            if (charId.isBlank() || content.isBlank()) continue

            // 查找是否是已关注的角色
            val authorProf = twitterRepo.getProfile(charId) ?: continue

            val realParentId = if (parentIdStr.startsWith("reply_index_")) {
                indexToIdMap[parentIdStr] ?: tweetId
            } else {
                tweetId
            }

            val finalTime = lastTime + offsetSec * 1000L
            lastTime = finalTime

            val replyUuid = UUID.randomUUID().toString()
            indexToIdMap["reply_index_$i"] = replyUuid

            val newReply = Tweet(
                id = replyUuid,
                authorId = charId,
                content = content,
                imagePath = null,
                timestamp = finalTime,
                parentId = realParentId,
                replyToUsername = replyToUser
            )
            twitterRepo.saveTweet(newReply)
            savedReplies.add(newReply)
        }

        // 8. 如果有回复，推进虚拟时间为最后一条回复的时间
        if (savedReplies.isNotEmpty()) {
            val maxTime = savedReplies.maxOf { it.timestamp }
            VirtualTimeManager.updateTime(maxTime)
        }

        savedReplies
    }

    /**
     * 在时间跳过 (Time Skip) 期间，模拟已关注 NPC 主动发布的推特动态
     */
    suspend fun simulateOfflineTweets(
        context: Context,
        startTimeMillis: Long,
        endTimeMillis: Long,
        userActivity: String
    ): List<Tweet> = withContext(Dispatchers.IO) {
        val twitterRepo = TwitterRepository(context)
        val followedProfiles = twitterRepo.getFollowedProfiles()
        if (followedProfiles.isEmpty()) return@withContext emptyList()

        val profileRepo = CharacterProfileRepository(context)
        val systemProfiles = profileRepo.getProfiles()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
        val startTimeStr = sdf.format(Date(startTimeMillis))
        val endTimeStr = sdf.format(Date(endTimeMillis))

        // 1. 组装角色提示词与通用上下文（融合私聊/线下互动/日记/以往推特的记忆）
        val charactersInfo = StringBuilder()
        val maxContextSize = com.moonlib.cosmos.data.settings.AiSettingsRepository(context).getMaxContextSize().coerceAtMost(30)
        for (fProf in followedProfiles) {
            val systemProf = systemProfiles.firstOrNull { it.id == fProf.characterId } ?: continue
            val promptProcessed = systemProf.prompt
                .replace("{{char}}", fProf.nickname)
                .replace("{{user}}", "玩家")
                
            // 获取该 NPC 的全局合并通用上下文记忆
            val recentMerged = ConversationContextBuilder.buildForCharacter(context, systemProf, maxContextSize)
            val unifiedMemoryText = if (recentMerged.isEmpty()) {
                "（当前暂无与玩家的共同记忆与沟通历史）"
            } else {
                recentMerged.joinToString("\n") { msg ->
                    val senderName = if (msg.senderId == "user") "玩家" else fProf.nickname
                    "- [时间: ${sdf.format(Date(msg.timestamp))}] $senderName 的${msg.prefix}: ${msg.content}"
                }
            }

            charactersInfo.append("角色 ID (character_id): ${fProf.characterId}\n")
            charactersInfo.append("推特名字: ${fProf.nickname}\n")
            charactersInfo.append("用户名: @${fProf.username}\n")
            charactersInfo.append("【人设日常作息与发帖口味】：\n$promptProcessed\n")
            charactersInfo.append("【该角色拥有的最新通用融合记忆（含线上私聊、线下互动、日记及以往推特）】：\n$unifiedMemoryText\n")
            charactersInfo.append("=========================================\n\n")
        }

        // 2. 系统提示词基底
        val systemPromptRepo = SystemPromptRepository(context)
        val mainPrompt = systemPromptRepo.getMainPromptContent()

        val systemPrompt = """
            $mainPrompt
            
            【CosmOS 虚拟手机推特 - 离线主动发推模拟系统】
            你现在正扮演 CosmOS 的“推特离线动态模拟器”。
            用户在此期间执行了“时间跳过”，处于离线状态。你需要根据被关注角色的作息习惯、最近的生活剧情，判定在这一时间区间内，是否有角色会主动发推特动态（如发自拍、日常感慨、生活图景等）。
            
            【离线区间与玩家状态】：
            - 起始时间：$startTimeStr
            - 结束时间：$endTimeStr
            - 玩家在此期间所做的事：${if (userActivity.isBlank()) "日常活动/睡觉" else userActivity}
            
            【已关注角色人设列表】：
            -----------------------------------------
            $charactersInfo
            -----------------------------------------
            
            【决策要求】：
            1. **拟真动态**：每个角色在这段时间内最多发 1 条推特动态，有的角色则完全不发，让整个时间线保持真实的物理感与松散的质感。
            2. **文字口吻**：文字内容严禁 emoji、颜文字及动作描写。字数控制在 40 字以内。必须以第二人称“你”代指玩家。
            3. **图文支持**：有些推文可以附带图片。如果附图，请将 `"has_image"` 设为 true，并写下极其生动的 `"image_description"` 画面文字描述（例如：『一张金黄的银杏树落叶铺满路面的特写，微风吹过，很有秋天悠闲的气息』），不需要包含实际图片路径，我们会将画面文字以高保真卡片渲染在前端。
            
            【底层通信输出格式】：
            为了与其他系统集成，你必须以 JSON 格式输出，不要包含任何 markdown 块。你的输出必须能够被直接解析为以下 JSON 格式：
            {
              "tweets": [
                {
                  "character_id": "发推角色的 character_id",
                  "content": "今天的早饭烤糊了，难过...",
                  "has_image": true,
                  "image_description": "一块表面烤得焦黑的吐司面包，旁边放着一杯热气腾腾的黑咖啡",
                  "time": "yyyy-MM-dd HH:mm:ss"
                }
              ]
            }
        """.trimIndent()

        // 3. AI 请求
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile() ?: return@withContext emptyList()
        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        val isNativeGenerateContent = activeProfile.serviceType == AiServiceType.VERTEX ||
                (activeProfile.serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com"))

        val responseText = if (isNativeGenerateContent) {
            executeGeminiOfficial(baseUrl, modelName, apiKey, temperature, systemPrompt, activeProfile.serviceType, activeProfile.vertexRegion)
        } else {
            executeOpenAI(baseUrl, modelName, apiKey, temperature, systemPrompt, activeProfile.serviceType, activeProfile.thinkingLevel)
        }

        // 保存通讯日志
        try {
            val logRepo = com.moonlib.cosmos.data.settings.AiLogRepository(context)
            logRepo.saveLog(
                characterName = "推特离线主动发推模拟",
                modelName = modelName,
                userInput = "时间跳过: $startTimeStr -> $endTimeStr",
                aiResponse = responseText,
                prompt = systemPrompt
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. 解析并保存模拟推文
        val cleanJson = cleanJsonResponse(responseText)
        val jsonObj = JSONObject(cleanJson)
        val tweetsArray = jsonObj.optJSONArray("tweets") ?: JSONArray()
        
        val simulatedTweets = mutableListOf<Tweet>()
        val sdfParser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (i in 0 until tweetsArray.length()) {
            val tObj = tweetsArray.getJSONObject(i)
            val charId = tObj.optString("character_id", "")
            val content = tObj.optString("content", "")
            val hasImage = tObj.optBoolean("has_image", false)
            val imgDesc = tObj.optString("image_description", "")
            val timeStr = tObj.optString("time", "")

            if (charId.isBlank() || content.isBlank() || timeStr.isBlank()) continue

            // 确认是已关注的角色
            if (twitterRepo.getProfile(charId) == null) continue

            val tweetTime = try {
                sdfParser.parse(timeStr)?.time ?: (startTimeMillis + (endTimeMillis - startTimeMillis) / 2)
            } catch (e: Exception) {
                startTimeMillis + (endTimeMillis - startTimeMillis) / 2
            }
            // 夹在起止时间段内
            val boundedTime = tweetTime.coerceIn(startTimeMillis, endTimeMillis)

            val imagePathVal = if (hasImage && imgDesc.isNotBlank()) {
                "simulated_image:$imgDesc"
            } else {
                null
            }

            val simulatedTweet = Tweet(
                id = UUID.randomUUID().toString(),
                authorId = charId,
                content = content,
                imagePath = imagePathVal,
                timestamp = boundedTime,
                parentId = null
            )
            twitterRepo.saveTweet(simulatedTweet)
            simulatedTweets.add(simulatedTweet)
        }

        simulatedTweets
    }

    // ── 内部辅助与请求函数 ──────────────────────────────────────────

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

    private fun executeGeminiOfficial(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
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
        conn.connectTimeout = 45000
        conn.readTimeout = 45000
        conn.setRequestProperty("Content-Type", "application/json")
        if (serviceType == AiServiceType.VERTEX) {
            conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
        }
        conn.doOutput = true

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
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

    private fun executeOpenAI(
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
        conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", systemPrompt)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", temperature.toDouble())
            put("max_tokens", 2048)
            AiReasoningRequestOptions.applyTo(this, serviceType, modelName, thinkingLevel)
            
            // 使用 JSON Mode
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
