package com.moonlib.cosmos.data.time

/**
 * 时间跳过历史记录。
 *
 * 职责单一：描述一次从起点到终点的时间跳过，以及玩家在此期间的活动。
 */
data class TimeSkipHistoryEntry(
    val id: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val userActivity: String,
    val createdAt: Long
)
