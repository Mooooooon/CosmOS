package com.moonlib.cosmos.data.ai

import com.moonlib.cosmos.data.settings.AiSceneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPipelineTest {

    @Test
    fun composeUsesRequiredSectionOrder() {
        val prompt = AiPromptComposer.compose(
            AiSceneRequest(
                sceneType = AiSceneType.CHAT,
                systemPrompt = "system",
                personaPrompt = "persona",
                outputRequirement = "output",
                jsonStructure = "json",
                historyText = "history",
                statusCard = "status",
                userInput = "user",
                logCharacterName = "test"
            )
        )

        val expectedOrder = listOf("系统提示词", "人设提示词", "输出要求", "JSON结构", "历史记录", "状态卡", "用户最新的发言")
        val actualOrder = expectedOrder.map { prompt.indexOf("【$it】") }
        assertTrue(actualOrder.all { it >= 0 })
        assertEquals(actualOrder.sorted(), actualOrder)
    }

    @Test
    fun cleanJsonRemovesMarkdownAndThinkingText() {
        val raw = """
            <thinking>内部推理</thinking>
            ```json
            {"replies":[{"content":"你好"}]}
            ```
        """.trimIndent()

        assertEquals("""{"replies":[{"content":"你好"}]}""", AiResponseCleaner.cleanJson(raw))
    }

    @Test
    fun cleanJsonExtractsObjectFromExtraText() {
        val raw = "好的，结果如下：{\"content\":\"正文\",\"summary\":\"摘要\"} 请查收"

        assertEquals("""{"content":"正文","summary":"摘要"}""", AiResponseCleaner.cleanJson(raw))
    }
}
