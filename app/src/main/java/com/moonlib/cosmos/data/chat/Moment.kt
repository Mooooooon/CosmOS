package com.moonlib.cosmos.data.chat

/**
 * 单条动态/动态回复数据类（朋友圈）
 * 
 * 职责单一：作为聊天 App 内部独立“动态”的内容载体以及评论树节点关系纯数据类。
 */
data class Moment(
    val id: String,
    val authorId: String,          // 作者 ID ("user" 或 characterId)
    val content: String,           // 动态/评论正文
    val imagePath: String?,        // 模拟配图描述（格式为 simulated_image:描述）
    val videoPath: String?,        // 模拟视频描述（格式为 simulated_video:描述）
    val timestamp: Long,           // 发送时间戳 (毫秒)
    val parentId: String?,         // 若为评论回复，指向父动态/回复的 ID；普通动态为 null
    val replyToUsername: String? = null // 回复的对应作者用户名 (无须带 @)
)
