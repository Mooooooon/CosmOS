package com.moonlib.cosmos.data.desktop

import android.content.Context
import com.moonlib.cosmos.ui.desktop.DesktopApp

/**
 * 桌面布局持久化仓库。
 *
 * 职责单一：保存并恢复桌面 App 图标的排列顺序。
 */
class DesktopLayoutRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSlots(defaultApps: List<DesktopApp>, slotCount: Int): List<DesktopApp?> {
        val savedIds = prefs.getString(KEY_APP_ORDER, null)
            ?.split(ORDER_SEPARATOR)
            .orEmpty()

        if (savedIds.isEmpty()) return defaultApps.normalizeSlots(slotCount)

        val appsById = defaultApps.associateBy(DesktopApp::id)
        val savedSlots = savedIds.map { id ->
            appsById[id]
        }
        return savedSlots.normalizeSlots(slotCount, defaultApps)
    }

    fun saveSlots(slots: List<DesktopApp?>) {
        val order = slots.joinToString(ORDER_SEPARATOR) { app ->
            app?.id ?: EMPTY_SLOT
        }
        prefs.edit().putString(KEY_APP_ORDER, order).apply()
    }

    private companion object {
        const val PREF_NAME = "cosmos_desktop_layout_prefs"
        const val KEY_APP_ORDER = "app_order"
        const val ORDER_SEPARATOR = ","
        const val EMPTY_SLOT = "_"
    }
}

private fun List<DesktopApp?>.normalizeSlots(
    slotCount: Int,
    requiredApps: List<DesktopApp> = filterNotNull(),
): List<DesktopApp?> {
    val slots = take(slotCount).toMutableList().apply {
        repeat((slotCount - size).coerceAtLeast(0)) {
            add(null)
        }
    }
    val presentIds = slots.mapNotNull { it?.id }.toMutableSet()
    requiredApps.filterNot { it.id in presentIds }.forEach { app ->
        val emptyIndex = slots.indexOfFirst { it == null }
        if (emptyIndex >= 0) {
            slots[emptyIndex] = app
            presentIds += app.id
        }
    }
    return slots
}
