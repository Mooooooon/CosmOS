package com.moonlib.cosmos.data.time

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 虚拟世界时间管理器
 *
 * 职责单一：负责虚拟时间的本地持久化存储、全局状态广播（通过 StateFlow）以及手动推进逻辑。
 */
object VirtualTimeManager {
    private const val PREF_NAME = "cosmos_time_prefs"
    private const val KEY_VIRTUAL_TIME = "virtual_time_millis"

    private lateinit var prefs: SharedPreferences
    private val _currentTimeFlow = MutableStateFlow(System.currentTimeMillis())
    
    /** 全局响应式时间流，UI 组件可订阅以更新时钟而无需使用轮询 Ticker */
    val currentTimeFlow: StateFlow<Long> = _currentTimeFlow.asStateFlow()

    /**
     * 在 App 启动时初始化虚拟时间管理器
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 首次启动默认设为当前系统真实时间
        val savedTime = prefs.getLong(KEY_VIRTUAL_TIME, System.currentTimeMillis())
        _currentTimeFlow.value = savedTime
    }

    /**
     * 获取当前的虚拟时间（毫秒戳）
     */
    fun getCurrentTimeMillis(): Long {
        return _currentTimeFlow.value
    }

    /**
     * 推进虚拟时间（只允许单向向前推进）
     * @param newTimeMillis 推进到的目标时间戳
     */
    fun updateTime(newTimeMillis: Long) {
        if (newTimeMillis > _currentTimeFlow.value) {
            _currentTimeFlow.value = newTimeMillis
            if (::prefs.isInitialized) {
                prefs.edit().putLong(KEY_VIRTUAL_TIME, newTimeMillis).apply()
            }
        }
    }

    /**
     * 格式化当前的虚拟时间
     */
    fun formatTime(pattern: String): String {
        val instant = java.time.Instant.ofEpochMilli(_currentTimeFlow.value)
        val ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return ldt.format(DateTimeFormatter.ofPattern(pattern))
    }
}
