package com.moonlib.cosmos.data.chat

/**
 * 聊天 APP 消息数据类
 *
 * 职责单一：负责表示单条聊天消息的数据结构。
 */
data class ChatMessage(
    val id: String,
    val senderId: String,     // 发送方 ID ("user" 表示用户，或者 contactId/characterId 表示联系人)
    val content: String,      // 消息内容
    val timestamp: Long,      // 发送时间戳
    val isPending: Boolean = false, // 是否正在发送中
    val type: String = "text", // 消息类型: text, image, video, voice, red_packet, transfer, location
    val extra: String? = null // 附加字段（如红包/转账状态，红包留言等）
)
