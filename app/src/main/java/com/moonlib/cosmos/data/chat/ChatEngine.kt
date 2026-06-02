package com.moonlib.cosmos.data.chat

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiHistoryItem
import com.moonlib.cosmos.data.ai.AiHistorySource
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.memory.MemoryCaptureParser
import com.moonlib.cosmos.data.memory.MemoryContextFormatter
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.profile.KeywordProfileMatcher
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
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

        val promptData = buildChatPromptData(
            context = context,
            contact = contact,
            chatNickname = contact.nickname,
            chatSignature = contact.signature
        )
        val recentMerged = promptData.recentMergedHistory

        val historyText = AiHistoryFormatter.formatHistoryItems(recentMerged)
        val userInputText = recentMerged.lastOrNull { it.senderId == "user" && it.source.isDirectConversation() }?.content ?: ""
        val result = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.CHAT,
                systemPrompt = promptData.systemPrompt,
                worldPrompt = promptData.worldPrompt,
                personaPrompt = promptData.personaPrompt,
                outputRequirement = promptData.outputRequirement,
                jsonStructure = promptData.jsonStructure,
                historyText = historyText,
                userInput = userInputText,
                logCharacterName = charProfile.name,
                logUserInput = userInputText,
                responseSchema = AiJsonSchemaFactory.chatRepliesSchema("cosmos_chat_replies")
            )
        )
        val responseText = result.rawResponse

        try {
            val jsonObj = JSONObject(AiResponseCleaner.cleanJson(responseText))
            MemoryCaptureParser.captureFromResponse(
                context = context,
                jsonObj = jsonObj,
                sceneType = AiSceneType.CHAT,
                fallbackCharacterIds = listOf(charProfile.id),
                validCharacterIds = profileRepo.getProfiles().map { it.id }.toSet()
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
            
            // 1. 尝试清洗可能混入的思考标签，获取纯文本回复
            val rawText = AiResponseCleaner.removeThinking(jsonStr)
            
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

    private fun AiHistorySource.isDirectConversation(): Boolean {
        return this == AiHistorySource.CHAT || this == AiHistorySource.INTERACTION
    }

    private data class ChatPromptData(
        val systemPrompt: String,
        val worldPrompt: String,
        val personaPrompt: String,
        val outputRequirement: String,
        val jsonStructure: String,
        val recentMergedHistory: List<AiHistoryItem>
    )

    private fun buildChatPromptData(
        context: Context,
        contact: ChatContact,
        chatNickname: String,
        chatSignature: String
    ): ChatPromptData {
        val chatRepo = ChatRepository(context)
        val profileRepo = CharacterProfileRepository(context)
        val charProfile = profileRepo.getProfiles().firstOrNull { it.id == contact.characterId }
            ?: throw Exception("关联的角色档案不存在，请检查或重新编辑该联系人资料。")
        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val userNickname = chatRepo.getUserNickname()
        val playerRealName = playerProfile?.name ?: userNickname
        val currentVirtualTime = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")
        val currentVirtualTimeWithWeekday = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")
        val processedCharPrompt = charProfile.prompt
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)
        val processedPlayerPrompt = (playerProfile?.prompt ?: "普通用户，无更多公开身份设定。")
            .replace("{{char}}", charProfile.name)
            .replace("{{user}}", playerRealName)
        val availableMessageTypes = if (charProfile.voiceId.isNotBlank()) {
            "text、voice、image、video、red_packet、transfer、location"
        } else {
            "text、image、video、red_packet、transfer、location"
        }
        val voiceRequirement = if (charProfile.voiceId.isNotBlank()) {
            "6. 可以按情境发送 text、voice、image、video、red_packet、transfer、location 类型消息；voice 表示一条可播放语音，content 填要说出口的短句。"
        } else {
            "6. 可以按情境发送 text、image、video、red_packet、transfer、location 类型消息；当前角色未绑定音色，禁止发送 voice 类型。"
        }

        // ── 提前构建 wide history，供关键词匹配和后续 Prompt 组装共用 ──
        val recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
            context = context,
            charProfiles = listOf(charProfile),
            maxContextSize = AiSettingsRepository(context).getMaxContextSize(),
            playerName = playerRealName
        )

        // ── 关键词匹配：从最近用户输入中检测是否提及其他角色 ──
        val savedChatUserInputs = chatRepo.getMessages(contact.id)
            .filter { it.senderId == "user" }
            .takeLast(5)
            .map { it.content }
        val latestUserInputFromHistory = recentMergedHistory
            .lastOrNull { it.senderId == "user" && it.source.isDirectConversation() }
            ?.content
        val recentUserInputsForKeyword = (savedChatUserInputs + listOfNotNull(latestUserInputFromHistory)).distinct()
        val candidateProfilesForKeyword = profileRepo.getProfiles().filter { p ->
            p.id != charProfile.id && !p.isPlayer
        }
        val mentionedPersonaAppend = KeywordProfileMatcher.buildAppendedPersonaText(
            matchedProfiles = KeywordProfileMatcher.match(
                recentUserInputs = recentUserInputsForKeyword,
                candidateProfiles = candidateProfilesForKeyword
            ),
            playerRealName = playerRealName
        )

        return ChatPromptData(
            systemPrompt = SystemPromptRepository(context).getMainPromptContent(),
            worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
            personaPrompt = """
                你现在正在扮演角色【${charProfile.name}】。
                
                【角色人设】
                $processedCharPrompt
                
                【用户人设】
                用户昵称：$userNickname
                用户真实姓名：$playerRealName
                $processedPlayerPrompt
            """.trimIndent() + mentionedPersonaAppend,
            outputRequirement = """
                当前场景：线上手机聊天。
                你正在通过 CosmOS 虚拟手机聊天软件与用户【$userNickname】远程聊天。
                你的聊天昵称是【$chatNickname】，个性签名是【$chatSignature】。
                当前虚拟世界时间：$currentVirtualTimeWithWeekday。
                
                回复要求：
                1. 必须百分之百扮演【${charProfile.name}】，不可 OOC。
                2. 回复应符合手机聊天特征：简洁、轻松、口语化。
                3. 单次回复 1 到 3 条消息，每条 1 到 3 句话，建议单条不超过 50 字；但在表白、倾诉心情、发表深刻感想等情感浓度较高的特殊场合，允许单条适当延长，以充分表达情感。
                4. 指代用户/玩家必须使用第二人称“你”，禁止使用“他/她”代指用户。
                5. 严禁 emoji、颜文字、表情符号和小括号动作描写。
                $voiceRequirement
                7. 不要在 content 中添加 [线上聊天] 等历史前缀。
                
                ${MemoryContextFormatter.CAPTURE_REQUIREMENT}
                
                【时空一致性（重要）】
                聊天消息之间往往只相隔几分钟甚至几秒，角色的位置和状态不可能发生突变。
                - 回复前必须根据对话历史判断角色当前所处的位置与状态（在家、在公司、在外出途中……）。
                - 生成的内容必须与这一状态完全吻合：如果上一条消息显示角色在家休息，本次回复中角色不可能突然出现在用户楼下或外出的场景。
                - 若角色想去某个地方，必须经历合理的时间推移（至少几十分钟）才能到达，不可在同一轮对话内瞬间到达。
                - 角色的情绪、话题、行为应保持自然的连续性，避免无原因的话题断裂或状态突变。
                
                【线下边界（重要）】
                本应用只负责远程手机聊天，面对面的线下互动由另一个独立系统接管，两者职责严格分离。
                - 当角色即将到达用户所在地点（如"我到楼下了""我进门了""我去你房间"），说明双方即将面对面，聊天 App 的职责就此结束。
                - 此时最多再发一条到达通知类短消息（如"到了"），之后不得继续通过聊天气泡描写任何线下互动行为、对话或场景。
                - 严禁在聊天消息中出现两人面对面时的动作、对话、感受或剧情推进——这些完全属于另一个系统的职责范围，不要越界。
            """.trimIndent(),
            jsonStructure = """
                {
                  "sender": "$chatNickname",
                  "replies": [
                    {
                      "type": "text",
                      "time": "yyyy-MM-dd HH:mm:ss",
                      "content": "纯聊天文本"
                    }
                  ],
                  "memories": []
                }
                
                约束：
                - replies 数组包含 1 到 3 条消息。
                - type 只能从 $availableMessageTypes 中选择。
                - time 必须晚于当前虚拟时间 $currentVirtualTime，并符合 yyyy-MM-dd HH:mm:ss。
                - 所有回复的 time 只能比当前虚拟时间晚 1 到 2 分钟，多条回复之间每条再递增 30 到 60 秒。严禁通过大幅跳跃 time（如跳跃数十分钟乃至数小时）来"合理化"角色位置或状态的突变——这是操纵剧情的违规行为。
                - red_packet 的 content 填金额，extra 可填祝福语；transfer 的 content 填金额。
                - location 的 content 填地名或地址。
                - voice 的 content 填语音中实际说出口的内容，长度建议 5 到 35 字，避免标点堆叠、动作描写和括号说明。
                - image 的 content 必须是一段生动具体的图片画面描述（20-50 字），描述画面中的主体、场景、氛围、色彩等细节，让人能在脑中清晰还原这张图片的样子，例如：「一张傍晚的街头照片，橙红色的晚霞铺满半边天，路灯刚刚亮起，行人撑着伞走过湿漉漉的人行道」。
                - video 的 content 必须是一段生动具体的视频内容描述（20-50 字），描述视频中的动态画面、声音氛围、场景与情绪，让人能感受到视频的现场感，例如：「一段在演唱会现场拍的视频，台上灯光闪烁变换，台下人群跟着节奏挥动荧光棒，背景音乐和欢呼声混在一起，手持拍摄略有抖动」。
                - 只返回纯 JSON，不要 markdown 代码块或解释文本。
            """.trimIndent(),
            recentMergedHistory = recentMergedHistory
        )
    }
}
