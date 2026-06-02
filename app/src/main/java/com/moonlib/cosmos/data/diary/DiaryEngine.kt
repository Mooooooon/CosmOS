package com.moonlib.cosmos.data.diary

import android.content.Context
import com.moonlib.cosmos.data.ai.AiHistoryFormatter
import com.moonlib.cosmos.data.ai.AiJsonSchemaFactory
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiResponseCleaner
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.ai.AiStatusUpdater
import com.moonlib.cosmos.data.context.ConversationContextBuilder
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.memory.MemoryCaptureParser
import com.moonlib.cosmos.data.memory.MemoryContextFormatter
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.profile.KeywordProfileMatcher
import com.moonlib.cosmos.data.settings.AiSettingsRepository
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import com.moonlib.cosmos.data.settings.SystemPromptRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
        val chatRepo = ChatRepository(context)
        val userNickname = chatRepo.getUserNickname()
        val playerRealName = playerProfile?.name ?: userNickname

        // 3. 组装 AI System Prompt 核心基底
        val mainPrompt = systemPromptRepo.getMainPromptContent()
        // 记录剧情开始时的虚拟时间（用于 DiaryEntry.virtualTime 及告知 AI 起始时间）
        val startVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss")
        val currentVirtualTimeStr = VirtualTimeManager.formatTime("yyyy-MM-dd HH:mm:ss EEEE")

        // 人称指示词
        val perspective = diaryRepo.getPerspective()
        val perspectiveInstruction = buildPerspectiveInstruction(
            perspective = perspective,
            playerRealName = playerRealName,
            characterNames = characterProfiles.joinToString("、") { it.name }
        )

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

        // ── 关键词匹配：检测当次 playerInput 是否提及未参与的其他角色 ──
        val involvedSet = involvedCharacterIds.toSet()
        val keywordCandidates = profileRepo.getProfiles().filter { p ->
            p.id !in involvedSet && !p.isPlayer
        }
        val mentionedProfiles = KeywordProfileMatcher.match(
            recentUserInputs = listOf(playerInput),
            candidateProfiles = keywordCandidates,
            recentCount = 1
        )
        val mentionedPersonaAppend = KeywordProfileMatcher.buildAppendedPersonaText(
            matchedProfiles = mentionedProfiles,
            playerRealName = playerRealName
        )

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

        val recentMergedHistory = ConversationContextBuilder.buildWideHistoryForCharacters(
            context = context,
            charProfiles = characterProfiles,
            maxContextSize = AiSettingsRepository(context).getMaxContextSize(),
            playerName = playerRealName,
            diariesOverride = diariesContextOverride
        )

        val userPrompt = """
            【本次剧情的起因/引子（请据此展开创作）】：
            $playerInput
            
            请创作这段剧情并直接输出对应的 JSON 结构。
        """.trimIndent()

        // 5. 调用统一 AI 管线获取响应
        val historyText = AiHistoryFormatter.formatTimeline(
            items = recentMergedHistory,
            timestampOf = { it.timestamp },
            bodyOf = { "${it.senderName}: [${it.source.label}] ${it.content}" }
        )
        val result = AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.DIARY,
                systemPrompt = mainPrompt,
                worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                personaPrompt = """
                    【参与本次剧情的角色设定如下】
                    $charProfilesStr
                    
                    【玩家设定如下】
                    $processedPlayerPrompt
                    
                    【当前虚拟世界的时间】
                    $currentVirtualTimeStr
                """.trimIndent() + mentionedPersonaAppend,
                outputRequirement = """
                    你现在是一位负责推进剧情的叙事大师，掌控这个虚拟世界中参演角色的行动与感受。
                    玩家给出了一个剧情引子，你需要将其扩展为一段正在发生的线下场景剧情，而不是事后回顾或日记总结。
                    
                    人称要求：
                    $perspectiveInstruction
                    
                    剧情要求：
                    1. 用现在进行时的笔触描写过程、细节、对话与情感。
                    2. 正文建议 300 至 600 字，情节饱满、细节具体。
                    3. 剧情必须有自然节奏：起因、经过、高光时刻、自然收尾。
                    4. 禁止假大空套话和词语堆砌。
                    5. 严禁 emoji、颜文字和表情符号。
                    
                    ${MemoryContextFormatter.CAPTURE_REQUIREMENT}
                    
                    时间推进要求（重要）：
                    创作结束后，根据剧情内容为本段场景确定一个合理的结束时刻（nextTime）：
                    - 若种子含时间锚点词，优先对齐其自然结束时刻（如"吃午饭"→ 约 12:30–13:30，"看电影"→ 约 2–3 小时后）。
                    - 若无锚点词，则根据剧情规模估算跨度（简短互动约 15–45 分钟，丰富多环节场景可达数小时）。
                    - nextTime 必须是符合剧情自然节律的绝对时刻，禁止机械地在起始时间上加固定分钟数。
                """.trimIndent(),
                jsonStructure = """
                    {
                      "content": "高质量剧情场景完整正文，纯文字叙事",
                      "summary": "20到40字的一句话摘要",
                      "nextTime": "yyyy-MM-dd HH:mm，根据剧情场景与时长灵活推断的合理结束时刻",
                      "memories": []${if (diaryStatusCardEnabled && statusKeys.isNotEmpty()) ",\n                      \"status\": {\n                        \"角色名\": {\n                          \"词条名\": \"仅当状态改变时填写更新值，未改变则不输出或设为 null\"\n                        }\n                      }" else ""}
                    }
                    
                    约束：
                    - 只返回纯 JSON，不要 markdown 代码块或解释文本。
                    - nextTime 必须存在，格式严格为 yyyy-MM-dd HH:mm，且不早于起始时间。
                    - nextTime 的时刻必须符合剧情的自然节律，禁止随意给出与剧情内容不符的时间。
                """.trimIndent(),
                historyText = historyText,
                statusCard = charStatusPrompt,
                userInput = userPrompt,
                logCharacterName = "剧情推进 (共 ${characterProfiles.size} 人)",
                logUserInput = playerInput,
                responseSchema = AiJsonSchemaFactory.diarySchema()
            )
        )
        val responseText = result.rawResponse

        // 7. 解析大模型返回的 JSON
        val cleanJson = AiResponseCleaner.cleanJson(responseText)
        val jsonObj = JSONObject(cleanJson)
        MemoryCaptureParser.captureFromResponse(
            context = context,
            jsonObj = jsonObj,
            sceneType = AiSceneType.DIARY,
            fallbackCharacterIds = involvedCharacterIds,
            validCharacterIds = profileRepo.getProfiles().map { it.id }.toSet()
        )
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
        val statusMap = if (diaryStatusCardEnabled && statusKeys.isNotEmpty()) {
            AiStatusUpdater.updateMultipleCharacters(context, characterProfiles, jsonObj)
        } else {
            emptyMap()
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

    private fun buildPerspectiveInstruction(
        perspective: String,
        playerRealName: String,
        characterNames: String
    ): String {
        return if (perspective == "first") {
            "本次剧情创作必须采用第一视角，以用户/玩家自己（真实姓名为 $playerRealName，在叙事中自称为“我”）的视角作为叙述主体，生动描述“我”与参演角色（$characterNames）正在发生的相处过程、细节互动，以及“我”对她们的内心真实感受。"
        } else {
            "本次剧情创作必须采用第三视角，采用旁观者或上帝视角，以客观发展的口吻叙述玩家（真实姓名 $playerRealName）与参演角色（$characterNames）之间正在发生的故事经历与互动细节。"
        }
    }

}
