package com.moonlib.cosmos.data.ai

/**
 * 统一 Prompt 组合器。
 *
 * 职责单一：按照项目约定的固定顺序拼接 AI 请求内容。
 */
object AiPromptComposer {

    fun compose(request: AiSceneRequest): String {
        return listOf(
            AiPromptSection("系统提示词", request.systemPrompt),
            AiPromptSection("世界设定", request.worldPrompt),
            AiPromptSection("人设提示词", request.personaPrompt),
            AiPromptSection("输出要求", request.outputRequirement),
            AiPromptSection("JSON结构", request.jsonStructure),
            AiPromptSection("记忆列表", request.memoryText),
            AiPromptSection("历史记录", request.historyText),
            AiPromptSection("状态卡", request.statusCard),
            AiPromptSection("用户最新的发言", request.userInput)
        ).joinToString("\n\n") { section ->
            val body = section.content.ifBlank { "无" }
            "【${section.title}】\n$body"
        }
    }
}
