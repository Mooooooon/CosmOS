package com.moonlib.cosmos.data.twitter

/**
 * 单条推文/回复数据类
 * 
 * 职责单一：作为推文内容以及回复树节点关系的纯数据类。
 */
data class Tweet(
    val id: String,
    val authorId: String,          // 作者 ID ("user" 或 characterId)
    val content: String,           // 推文/回复内容
    val imagePath: String?,        // 可选的本地配图绝对路径
    val timestamp: Long,           // 发送时间戳 (毫秒)
    val parentId: String?,         // 若为回复，指向父推文/回复的 ID；普通发推为 null
    val replyToUsername: String? = null // 回复的对应作者用户名 (无须带 @，例如: "alice_wonderland")
)
