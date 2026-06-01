package com.moonlib.cosmos.data.interaction

/**
 * 互动 APP 消息数据类
 *
 * 职责单一：负责表示单条线下实体互动消息的数据结构（包含括号动作与普通对话）。
 */
data class InteractionMessage(
    val id: String,
    val senderId: String,     // 发送方 ID ("user" 表示用户，或者 characterId 表示角色)
    val content: String,      // 消息内容 (例如 "（看向对方）东西带了吗？")
    val timestamp: Long,      // 虚拟时间戳
    val isPending: Boolean = false, // 是否正在发送中
    val statusMap: Map<String, String>? = null // 本条消息完成时，角色实时状态的快照
)
