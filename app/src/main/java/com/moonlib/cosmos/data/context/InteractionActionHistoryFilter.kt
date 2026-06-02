package com.moonlib.cosmos.data.context

import com.moonlib.cosmos.data.interaction.InteractionMessage

/**
 * 互动历史动作过滤器。
 *
 * 职责单一：为 AI 上下文剥离较旧互动消息中的括号动作描写。
 */
object InteractionActionHistoryFilter {
    private const val DEFAULT_KEEP_ACTION_COUNT = 3
    private val actionSegmentRegex = Regex("（[^（）]*）|\\([^()]*\\)")
    private val whitespaceRegex = Regex("\\s+")

    fun filterForHistory(
        messages: List<InteractionMessage>,
        keepLatestActionCount: Int = DEFAULT_KEEP_ACTION_COUNT
    ): List<InteractionMessage> {
        if (messages.isEmpty()) return emptyList()

        val latestActionMessageIds = messages
            .sortedBy { it.timestamp }
            .takeLast(keepLatestActionCount.coerceAtLeast(0))
            .map { it.id }
            .toSet()

        return messages.mapNotNull { message ->
            if (message.id in latestActionMessageIds) {
                message
            } else {
                val contentWithoutActions = removeActionSegments(message.content)
                if (contentWithoutActions.isBlank()) {
                    null
                } else {
                    message.copy(content = contentWithoutActions)
                }
            }
        }
    }

    fun removeActionSegments(content: String): String {
        var current = content
        while (true) {
            val next = current.replace(actionSegmentRegex, "")
            if (next == current) break
            current = next
        }
        return current
            .replace(whitespaceRegex, " ")
            .trim()
    }
}
