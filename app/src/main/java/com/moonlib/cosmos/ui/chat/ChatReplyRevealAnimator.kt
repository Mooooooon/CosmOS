package com.moonlib.cosmos.ui.chat

import com.moonlib.cosmos.data.chat.ChatMessage
import kotlinx.coroutines.delay

/**
 * 聊天回复逐条揭示控制器。
 *
 * 职责单一：根据 AI 多条回复的文本长度计算节奏，并将回复逐条推送给 UI。
 */
suspend fun revealAiReplies(
    currentMessages: List<ChatMessage>,
    replies: List<ChatMessage>,
    onMessagesChanged: (List<ChatMessage>) -> Unit,
    onReplyRevealed: suspend () -> Unit
) {
    if (replies.isEmpty()) return

    var visibleMessages = currentMessages
    replies.forEachIndexed { index, reply ->
        if (index > 0) {
            delay(replyRevealDelayMillis(replies[index - 1].content))
        }

        visibleMessages = visibleMessages + reply
        onMessagesChanged(visibleMessages)
        onReplyRevealed()
    }
}

private fun replyRevealDelayMillis(content: String): Long {
    val trimmedLength = content.trim().length
    return (500L + trimmedLength * 35L).coerceIn(900L, 4_200L)
}
