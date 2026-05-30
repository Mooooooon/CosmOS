package com.moonlib.cosmos.data.interaction

/**
 * 融合上下文消息模型
 *
 * 职责单一：作为线上聊天、线下互动与剧情日记向 AI 提交历史记忆时的统一表达模型。
 */
enum class MergedMessageSource(val prefix: String) {
    CHAT("[线上聊天]"),
    INTERACTION("[线下互动]"),
    DIARY("[剧情日记]"),
    TWITTER("[推特动态]")
}

data class MergedMessage(
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isOnline: Boolean,     // true 表示这是 [线上聊天]，false 表示这是 [线下互动]；保留兼容旧调用
    val source: MergedMessageSource = if (isOnline) MergedMessageSource.CHAT else MergedMessageSource.INTERACTION
) {
    val prefix: String
        get() = source.prefix
}
