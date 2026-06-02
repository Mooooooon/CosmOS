package com.moonlib.cosmos.data.context

import com.moonlib.cosmos.data.interaction.InteractionMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionActionHistoryFilterTest {

    @Test
    fun keepsActionSegmentsOnlyForLatestThreeMessages() {
        val messages = listOf(
            message("1", "（低头整理衣袖）早上好。", 1L),
            message("2", "（心里有些犹豫）我想问你一件事。", 2L),
            message("3", "（抬眼看向你）你到啦。", 3L),
            message("4", "（轻声笑）坐吧。", 4L),
            message("5", "（把杯子推近）喝点水。", 5L)
        )

        val result = InteractionActionHistoryFilter.filterForHistory(messages)

        assertEquals("早上好。", result[0].content)
        assertEquals("我想问你一件事。", result[1].content)
        assertEquals("（抬眼看向你）你到啦。", result[2].content)
        assertEquals("（轻声笑）坐吧。", result[3].content)
        assertEquals("（把杯子推近）喝点水。", result[4].content)
    }

    @Test
    fun removesOldPureActionMessagesFromHistory() {
        val messages = listOf(
            message("1", "（只是沉默地望着窗外）", 1L),
            message("2", "（回过神）嗯。", 2L)
        )

        val result = InteractionActionHistoryFilter.filterForHistory(
            messages = messages,
            keepLatestActionCount = 1
        )

        assertEquals(1, result.size)
        assertEquals("2", result.first().id)
        assertEquals("（回过神）嗯。", result.first().content)
    }

    @Test
    fun stripsChineseAndEnglishParenthesizedSegments() {
        val result = InteractionActionHistoryFilter.removeActionSegments("（停顿）Hello (smiles) 继续说。")

        assertEquals("Hello 继续说。", result)
    }

    private fun message(id: String, content: String, timestamp: Long): InteractionMessage {
        return InteractionMessage(
            id = id,
            senderId = "char_1",
            content = content,
            timestamp = timestamp
        )
    }
}
