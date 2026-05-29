package com.moonlib.cosmos.data.chat

/**
 * 聊天 APP 联系人数据类
 *
 * 职责单一：表示联系人基础元数据。
 */
data class ChatContact(
    val id: String,
    val nickname: String,
    val avatar: String,       // 本地头像文件的绝对路径，若为空白则代表未上传
    val signature: String,    // 个性签名
    val characterId: String   // 关联的人设档案 ID (来自 CharacterProfile)
)
