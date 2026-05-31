package com.moonlib.cosmos.data.ai

import com.moonlib.cosmos.data.interaction.MergedMessage
import com.moonlib.cosmos.data.interaction.MergedMessageSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 历史记录格式化器。
 *
 * 职责单一：将按时间升序排列的历史记录格式化为带时间锚点的 Prompt 文本。
 */
object AiHistoryFormatter {
    private const val DEFAULT_TIME_GROUP_WINDOW_MILLIS = 10 * 60 * 1000L
    private const val DEFAULT_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss EEEE"

    fun formatMergedMessages(
        messages: List<MergedMessage>,
        timeGroupWindowMillis: Long = DEFAULT_TIME_GROUP_WINDOW_MILLIS
    ): String {
        return formatTimeline(
            items = messages,
            timestampOf = { it.timestamp },
            bodyOf = { "${it.roleNameForPrompt()}: ${it.prefix} ${it.content}" },
            timeGroupWindowMillis = timeGroupWindowMillis
        )
    }

    fun <T> formatTimeline(
        items: List<T>,
        timestampOf: (T) -> Long,
        bodyOf: (T) -> String,
        timeGroupWindowMillis: Long = DEFAULT_TIME_GROUP_WINDOW_MILLIS,
        timePattern: String = DEFAULT_TIME_PATTERN
    ): String {
        val formatter = SimpleDateFormat(timePattern, Locale.getDefault())
        var currentGroupStart: Long? = null

        return items.joinToString("\n") { item ->
            val timestamp = timestampOf(item)
            val shouldShowTime = currentGroupStart
                ?.let { timestamp - it > timeGroupWindowMillis }
                ?: true

            if (shouldShowTime) {
                currentGroupStart = timestamp
                "[时间: ${formatter.format(Date(timestamp))}] ${bodyOf(item)}"
            } else {
                bodyOf(item)
            }
        }
    }

    private fun MergedMessage.roleNameForPrompt(): String {
        return when (source) {
            MergedMessageSource.DIARY,
            MergedMessageSource.TWITTER,
            MergedMessageSource.MOMENT -> "记忆"
            else -> if (senderId == "user") "用户" else "你"
        }
    }
}
