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
    /**
     * 该角色的关键词列表（称呼、外号、代号等）。
     * 当用户最近 N 次输入中包含任意一个关键词时，该角色的人设会被自动追加到 personaPrompt 中。
     */
    val keywords: List<String> = emptyList(),
)
