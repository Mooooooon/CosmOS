package com.moonlib.cosmos.data.interaction

/**
 * 融合上下文消息模型
 *
 * 职责单一：作为线上【聊天 APP】和线下【互动 APP】向 AI 提交历史对话记忆时的统一表达模型。
 */
data class MergedMessage(
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isOnline: Boolean     // true 表示这是 [线上聊天]，false 表示这是 [线下互动]
)
