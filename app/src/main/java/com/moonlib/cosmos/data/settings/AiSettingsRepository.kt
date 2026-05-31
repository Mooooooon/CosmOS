package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局 AI 偏好设置仓储类
 * 职责单一：负责全局 AI 通讯参数（如最大上下文消息数）的读取与持久化保存
 */
class AiSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_ai_global_settings_prefs"
        private const val KEY_MAX_CONTEXT_SIZE = "max_context_size"
        private const val DEFAULT_MAX_CONTEXT_SIZE = 100

    }

    /**
     * 获取全局最大上下文消息条数（默认 100）
     */
    fun getMaxContextSize(): Int {
        return prefs.getInt(KEY_MAX_CONTEXT_SIZE, DEFAULT_MAX_CONTEXT_SIZE)
    }

    /**
     * 保存全局最大上下文消息条数
     */
    fun saveMaxContextSize(size: Int) {
        prefs.edit().putInt(KEY_MAX_CONTEXT_SIZE, size).apply()
    }

}
