package com.moonlib.cosmos.data.profile

/**
 * 用户和角色人设数据类
 *
 * 职责单一：负责表示单个人设提示词的元数据。
 */
data class CharacterProfile(
    val id: String,
    val name: String,
    val prompt: String,
    val isPlayer: Boolean,
    val avatar: String = "",
)
