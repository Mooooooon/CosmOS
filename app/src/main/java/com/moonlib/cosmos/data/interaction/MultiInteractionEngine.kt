package com.moonlib.cosmos.data.interaction

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.ai.AiStatusUpdater
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.memory.MemoryCaptureParser
import com.moonlib.cosmos.data.memory.MemoryContextFormatter
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * 多人线下互动回应引擎。
 *
 * 职责单一：为同一线下场景中的多位参与角色组织上下文、调用 AI，并落库共享回复。
 */
object MultiInteractionEngine {

    suspend fun getAiResponse(
        context: Context,
        participantIds: List<String>
    ): List<InteractionMessage> = withContext(Dispatchers.IO) {
        val participants = participantIds.distinct()
        if (participants.size < 2) {
            throw Exception("请选择至少两位参与本次互动的角色。")
        }

        val interactionRepo = InteractionRepository(context)
        val profileRepo = CharacterProfileRepository(context)
        val allProfiles = profileRepo.getProfiles()
        val characterProfiles = allProfiles
            .filter { it.id in participants && !it.isPlayer }
            .sortedBy { participants.indexOf(it.id) }
        if (characterProfiles.size < 2) {
            throw Exception("所选参与角色不足，请重新选择。")
        }

        val activeProfile = AiConfigRepository(context).getActiveProfile()
            ?: throw Exception("未检测到激活的 AI 模型。请前往【系统设置】配置模型服务。")
        if (activeProfile.apiKey.isBlank() || activeProfile.baseUrl.isBlank() || activeProfile.modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查。")
        }

        val promptData = buildPromptData(context, characterProfiles)
        val historyText = AiHistoryFormatter.formatHistoryItems(promptData.recentMergedHistory)
        val userInputText = promptData.recentMergedHistory
            .lastOrNull { it.senderId == "user" && it.source == com.moonlib.cosmos.data.ai.AiHistorySource.INTERACTION }
            ?.content
            ?: ""

        val result = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.INTERACTION,
                systemPrompt = promptData.systemPrompt,
                worldPrompt = promptData.worldPrompt,
                personaPrompt = promptData.personaPrompt,
                outputRequirement = promptData.outputRequirement,
                jsonStructure = promptData.jsonStructure,
                memoryText = promptData.memoryText,
                historyText = historyText,
                statusCard = promptData.statusPrompt,
                userInput = userInputText,
                logCharacterName = "多人互动 (共 ${characterProfiles.size} 人)",
                logUserInput = userInputText,
                responseSchema = AiJsonSchemaFactory.multiInteractionRepliesSchema()
            )
        )

        val responseText = result.rawResponse
        val validCharacterIds = allProfiles.map { it.id }.toSet()
        val cleanJson = AiResponseCleaner.cleanJson(responseText)
        val jsonObj = JSONObject(cleanJson)

        MemoryCaptureParser.captureFromResponse(
            context = context,
            jsonObj = jsonObj,
            sceneType = AiSceneType.INTERACTION,
            fallbackCharacterIds = characterProfiles.map { it.id },
            validCharacterIds = validCharacterIds
        )

        if (promptData.isStatusEnabled) {
            AiStatusUpdater.updateMultipleCharacters(context, characterProfiles, jsonObj)
        }

        val currentTime = VirtualTimeManager.getCurrentTimeMillis()
        val sceneId = interactionRepo.getMessages(characterProfiles.first().id)
            .lastOrNull { it.senderId == "user" && it.participantIds.containsAll(characterProfiles.map { profile -> profile.id }) }
            ?.sceneId
            ?: UUID.randomUUID().toString()
        val aiMessages = parseAiResponseJson(
            jsonStr = responseText,
            profilesById = characterProfiles.associateBy { it.id },
            defaultTimeMillis = currentTime,
            sceneId = sceneId,
            participantIds = characterProfiles.map { it.id }
        )

        val statusSnapshot = if (promptData.isStatusEnabled) {
            val settingsRepo = InteractionSettingsRepository(context)
            characterProfiles.associate { it.id to settingsRepo.getCharacterStatus(it.id) }
        } else {
            emptyMap()
        }

        val messagesWithStatus = aiMessages.map { message ->
            message.copy(statusMapByCharacterId = statusSnapshot.ifEmpty { null })
        }
        interactionRepo.saveSharedMessages(characterProfiles.map { it.id }, messagesWithStatus)

        if (messagesWithStatus.isNotEmpty()) {
            VirtualTimeManager.updateTime(messagesWithStatus.maxOf { it.timestamp })
        }

        messagesWithStatus
    }

    private data class PromptData(
        val systemPrompt: String,
        val worldPrompt: String,
        val personaPrompt: String,
        val outputRequirement: String,
        val jsonStructure: String,
        val memoryText: String,
        val statusPrompt: String,
        val recentMergedHistory: List<com.moonlib.cosmos.data.ai.AiHistoryItem>,
        val isStatusEnabled: Boolean
    )

    private fun buildPromptData(
        context: Context,
        characterProfiles: List<CharacterProfile>
    ): PromptData {
        val chatRepo = ChatRepository(context)
        val profileRepo = CharacterProfileRepository(context)
        val userNickname = chatRepo.getUserNickname()
        val playerProfile = profileRepo.getProfiles().firstOrNull { it.isPlayer }
        val playerRealName = playerProfile?.name ?: userNickname
        val currentWorldTime = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")
        val firstCharacterName = characterProfiles.firstOrNull()?.name ?: "角色"

        val characterPersona = characterProfiles.joinToString("\n\n") { character ->
            val prompt = character.prompt
                .replace("{{char}}", character.name)
                .replace("{{user}}", playerRealName)
            "角色【${character.name}】\n$prompt"
        }
        val playerPrompt = (playerProfile?.prompt ?: "普通用户，无更多公开身份设定。")
            .replace("{{char}}", firstCharacterName)
            .replace("{{user}}", playerRealName)

        val recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
            context = context,
            charProfiles = characterProfiles,
            maxContextSize = AiSettingsRepository(context).getMaxContextSize(),
            playerName = playerRealName
        )
        val memoryText = ConversationContextBuilder.buildMemoryListForCharacters(
            context = context,
            charProfiles = characterProfiles
        )

        val settingsRepo = InteractionSettingsRepository(context)
        val statusKeys = settingsRepo.getStatusKeys()
        val isStatusEnabled = settingsRepo.isStatusCardEnabled() && statusKeys.isNotEmpty()
        val statusPrompt = if (isStatusEnabled) {
            buildString {
                appendLine("当前参与角色状态卡已开启，请维护以下状态词条：")
                characterProfiles.forEach { character ->
                    val currentStatus = settingsRepo.getCharacterStatus(character.id)
                    val statusText = statusKeys.joinToString("；") { key ->
                        val currentVal = currentStatus[key.name] ?: "未知"
                        "「${key.name}」（${key.description}）：$currentVal"
                    }
                    appendLine("- 角色【${character.name}】：$statusText")
                }
                appendLine()
                appendLine("状态更新要求：")
                appendLine("1. 只有身体姿势、动作、神态、物理位置、服装衣着等发生改变时才输出 status。")
                appendLine("2. status 按角色名或角色 ID 分组，未改变的词条不要输出，或设为 null。")
                appendLine("3. 状态值必须是纯描述，不能包含中文小括号或英文小括号。")
                appendLine("4. 状态值中指代用户必须使用第二人称“你”。")
            }
        } else {
            ""
        }

        return PromptData(
            systemPrompt = SystemPromptRepository(context).getMainPromptContent(),
            worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
            personaPrompt = """
                你现在需要同时扮演以下线下场景参与角色。
                
                【角色设定】
                $characterPersona
                
                【用户设定】
                用户昵称：$userNickname
                用户真实姓名：$playerRealName
                $playerPrompt
                
                【当前世界时间】
                $currentWorldTime
            """.trimIndent(),
            outputRequirement = """
                当前场景：多人线下面对面互动。
                用户【$playerRealName】与所有参与角色共享同一个现场互动界面。
                
                回复要求：
                1. 每条回复必须明确属于某一位参与角色，并通过 character_id 输出该角色 ID。
                2. 根据现场氛围自然决定 1 到多位角色回应，不要求所有角色都发言。
                3. 每条回复必须包含中文小括号动作描写，例如“（抬眼看向你）我也想听听。”。
                4. 动作、神态、语气、心理和姿势应具体写实，符合各自人设。
                5. 指代用户/玩家必须使用第二人称“你”，禁止使用“他/她”代指用户。
                6. 严禁 emoji、颜文字和表情符号。
                7. 单次回复 1 到 5 条，每条 1 到 3 句话，建议单条不超过 70 字。
                8. 如果状态卡发生改变，按“状态卡”段落要求在 JSON 外层输出 status。
                
                ${MemoryContextFormatter.CAPTURE_REQUIREMENT}
            """.trimIndent(),
            jsonStructure = """
                {
                  "replies": [
                    {
                      "character_id": "${characterProfiles.first().id}",
                      "time": "yyyy-MM-dd HH:mm:ss",
                      "content": "（动作描写）回复内容"
                    }
                  ],
                  "memories": []${if (isStatusEnabled) ",\n                  \"status\": {\n                    \"角色名或角色ID\": {\n                      \"词条名称\": \"仅当该词条状态发生改变时更新的值，未改变的词条不输出或设为 null\"\n                    }\n                  }" else ""}
                }
                
                约束：
                - replies 数组包含 1 到 5 条消息。
                - character_id 必须来自本次参与角色 ID：${characterProfiles.joinToString("、") { it.id }}。
                - time 必须晚于当前世界时间，并符合 yyyy-MM-dd HH:mm:ss。
                - content 必须带中文小括号动作描写。
                - status 值不能包含中文或英文小括号。
                - 只返回纯 JSON，不要 markdown 代码块或解释文本。
            """.trimIndent(),
            memoryText = memoryText,
            statusPrompt = statusPrompt,
            recentMergedHistory = recentMergedHistory,
            isStatusEnabled = isStatusEnabled
        )
    }

    private fun parseAiResponseJson(
        jsonStr: String,
        profilesById: Map<String, CharacterProfile>,
        defaultTimeMillis: Long,
        sceneId: String,
        participantIds: List<String>
    ): List<InteractionMessage> {
        val cleanJson = AiResponseCleaner.cleanJson(jsonStr)
        val jsonObj = JSONObject(cleanJson)
        val repliesArray = jsonObj.getJSONArray("replies")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val list = mutableListOf<InteractionMessage>()
        var lastTime = defaultTimeMillis

        for (i in 0 until repliesArray.length()) {
            val replyObj = repliesArray.getJSONObject(i)
            val characterId = replyObj.optString("character_id", "")
            val content = replyObj.optString("content", "").trim()
            if (profilesById[characterId] == null || content.isBlank()) continue

            val parsedTime = try {
                val timeStr = replyObj.optString("time", "")
                if (timeStr.isNotBlank()) sdf.parse(timeStr)?.time else null
            } catch (e: Exception) {
                null
            }
            val finalTime = when {
                parsedTime != null && parsedTime > lastTime -> parsedTime
                else -> lastTime + 5000L
            }
            lastTime = finalTime
            list.add(
                InteractionMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = characterId,
                    content = content,
                    timestamp = finalTime,
                    sceneId = sceneId,
                    participantIds = participantIds
                )
            )
        }

        if (list.isEmpty()) {
            throw Exception("AI 没有返回有效的多人互动内容。")
        }
        return list
    }
}
