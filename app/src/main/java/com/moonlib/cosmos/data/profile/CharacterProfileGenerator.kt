package com.moonlib.cosmos.data.profile

import android.content.Context
import com.moonlib.cosmos.data.ai.AiRequestClient
import com.moonlib.cosmos.data.ai.AiSceneRequest
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiSceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.moonlib.cosmos.data.settings.SystemPromptRepository

/**
 * 异步人设提示词生成助手
 * 
 * 职责单一：负责结合当前激活的 AI 模型，根据用户想法异步生成高保真 Markdown 人设配置。
 */
object CharacterProfileGenerator {

    /**
     * 系统指令：强行约束 AI 生成特定的结构，并强制执行 {{char}} 替代人名
     */
    private val SYSTEM_PROMPT = """
        你是一个专业的小说与角色扮演游戏人设设计师。
        请根据用户提供的人设核心想法，为角色或用户精心设计出富有深度、细节完整的人物设定。
        你必须完全遵守并按照以下指定的 Markdown 模板返回数据，严禁携带任何废话、Markdown包裹之外的前言、后语或解释说明，直接输出Markdown文本：
        
        # {{char}}
        ## 基本信息
        
        ## 身份背景
        
        ## 外貌身材设定
        
        ## 性格设定
        
        ## 穿衣风格
        1. A场合
        2. B场合
        
        ## 人际关系
        角色1
        角色2
        
        【重要核心规则】：
        1. 必须完全使用上面指定的二级标题模板，绝对不要自作聪明去增减、更改任何一级和二级标题！
        2. 在生成的内容中，凡是需要提到或出现该角色（设定主体）姓名的地方，你必须百分之百使用变量“{{char}}”代替。
        3. 在生成的内容中，凡是需要提到或出现“我”或用户/用户姓名的地方，你必须百分之百使用变量“{{user}}”代替。
        4. 绝对不要在任何地方直接输出真实的姓名，确保角色扮演机制能完美动态匹配！
        5. 无论是描述任何性格、背景、对话示例还是其他段落，严禁在内容中包含任何 emoji、表情符号或颜文字（如 😊, 😂, (๑•̀ㅂ•́)و✧ 等）。所有生成文本必须完全使用纯文本。
    """.trimIndent()

    /**
     * 发起网络请求，异步生成人设
     */
    suspend fun generateProfile(
        context: Context,
        userIdea: String,
        playerProfile: CharacterProfile? = null,
        referenceProfiles: List<CharacterProfile> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val configRepo = AiConfigRepository(context)
        val activeProfile = configRepo.getActiveProfile()
            ?: throw Exception("请先前往【系统设置 -> AI模型服务配置】中添加并激活至少一个 AI 模型服务商。")

        val apiKey = activeProfile.apiKey
        val baseUrl = activeProfile.baseUrl
        val modelName = activeProfile.modelName
        val temperature = activeProfile.temperature

        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查配置。")
        }

        AiRequestClient.execute(
            context = context,
            request = AiSceneRequest(
                sceneType = AiSceneType.PROFILE_GENERATION,
                systemPrompt = SYSTEM_PROMPT,
                worldPrompt = SystemPromptRepository(context).getWorldPromptContent(),
                personaPrompt = buildReferencePrompt(playerProfile, referenceProfiles),
                outputRequirement = "必须完全按照指定 Markdown 模板直接输出，不要解释，不要使用 JSON，不要包含 emoji 或颜文字。",
                jsonStructure = "非 JSON 输出：直接返回 Markdown 人设文本。",
                userInput = "我的核心想法是：$userIdea。请完全按照指定格式生成，并全程用 {{char}} 代替角色人名。",
                logCharacterName = "人设生成器",
                logUserInput = userIdea,
                expectsJson = false
            )
        ).rawResponse.trim()
    }

    private fun buildReferencePrompt(
        playerProfile: CharacterProfile?,
        referenceProfiles: List<CharacterProfile>
    ): String {
        val validReferences = referenceProfiles.filter { !it.isPlayer && it.prompt.isNotBlank() }
        return buildString {
            append("这是一次独立的人设生成任务，不纳入角色扮演历史链。")
            if (playerProfile != null && playerProfile.prompt.isNotBlank()) {
                append("\n\n【用户人设（用于建立新角色与 {{user}} 的关系）】\n")
                appendProfile(playerProfile)
            }
            if (validReferences.isNotEmpty()) {
                append("\n\n【已有角色参考（用于生成朋友、亲人、同事等关系，不要直接复制）】\n")
                validReferences.forEach { profile ->
                    appendProfile(profile)
                }
                append("请优先参考这些既有设定中的关系、口吻、背景与世界观连续性，生成的新角色仍必须是独立完整的人设。\n")
            }
        }
    }

    private fun StringBuilder.appendProfile(profile: CharacterProfile) {
        append("姓名：${profile.name}\n")
        append("人设：\n")
        append(profile.prompt.trim())
        append("\n\n")
    }

}
