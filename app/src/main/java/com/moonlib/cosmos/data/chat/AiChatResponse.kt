package com.moonlib.cosmos.data.chat

/**
 * AI 底层通用通讯消息单条回复对象
 */
data class AiReplyItem(
    val type: String = "text",      // 回复类型，如 "text", "action", "image" 等，默认为 "text"
    val time: String,               // 虚拟时间戳，格式: "yyyy-MM-dd HH:mm:ss"
    val content: String             // 回复内容
)

/**
 * AI 底层通用通讯顶层 JSON 对象
 */
data class AiChatResponse(
    val sender: String,             // 发送人（角色名）
    val replies: List<AiReplyItem>  // 多条连续回复的数组
)
