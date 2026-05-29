package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 主题设置持久化仓库类
 * 职责单一：负责管理浅色/深色主题配置的持久化存储。
 */
class ThemeSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_theme_settings_prefs"
        private const val KEY_IS_DARK = "is_dark_theme"
    }

    /**
     * 当前是否为深色模式，默认为 true (深色)
     */
    fun isDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_IS_DARK, true)
    }

    /**
     * 保存当前主题模式
     */
    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()
    }
}
