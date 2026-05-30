package com.moonlib.cosmos.data.diary

import java.util.UUID

/**
 * 单篇日记的数据模型
 * 
 * @property id 唯一标识
 * @property timestamp 日记创建的真实时间戳
 * @property virtualTime 日记记录时的虚拟时间
 * @property playerInput 用户的剧情起因或引子输入
 * @property content AI 生成的日记完整正文
 * @property summary AI 生成的日记简短摘要 (用于上下文拼接节约 token)
 * @property involvedCharacterIds 本篇日记参与的所有角色 ID
 * @property statusMap 本篇日记完成时，参与角色实时状态的快照 (角色ID -> (状态词条名 -> 状态值))
 */
data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val virtualTime: String,
    val playerInput: String,
    val content: String,
    val summary: String,
    val involvedCharacterIds: List<String>,
    val statusMap: Map<String, Map<String, String>> = emptyMap()
)
