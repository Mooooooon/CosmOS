package com.moonlib.cosmos.data.memory

import java.util.UUID

/**
 * 长期记忆条目。
 *
 * 职责单一：描述一条可被查看、编辑并注入剧情上下文的长期记忆。
 */
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val characterIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val importance: Int = 1,
    val sourceScene: String = "manual",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isContextEnabled: Boolean = true
)
