package com.moonlib.cosmos.data.time

import android.content.Context
import android.content.SharedPreferences
import com.moonlib.cosmos.data.settings.SaveManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 时间跳过历史仓库。
 *
 * 职责单一：保存与读取每次时间跳过留下的上下文锚点。
 */
class TimeSkipHistoryRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    fun getEntries(): List<TimeSkipHistoryEntry> {
        val jsonString = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    add(parseEntry(jsonArray.getJSONObject(i)))
                }
            }.sortedBy { it.endTimeMillis }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun addEntry(entry: TimeSkipHistoryEntry) {
        val entries = (getEntries() + entry)
            .distinctBy { it.id }
            .sortedBy { it.endTimeMillis }
            .takeLast(MAX_ENTRIES)
        saveEntries(entries)
    }

    private fun saveEntries(entries: List<TimeSkipHistoryEntry>) {
        try {
            val jsonArray = JSONArray()
            entries.forEach { jsonArray.put(serializeEntry(it)) }
            prefs.edit().putString(KEY_ENTRIES, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseEntry(json: JSONObject): TimeSkipHistoryEntry {
        return TimeSkipHistoryEntry(
            id = json.getString("id"),
            startTimeMillis = json.getLong("startTimeMillis"),
            endTimeMillis = json.getLong("endTimeMillis"),
            userActivity = json.optString("userActivity", ""),
            createdAt = json.optLong("createdAt", json.getLong("endTimeMillis"))
        )
    }

    private fun serializeEntry(entry: TimeSkipHistoryEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("startTimeMillis", entry.startTimeMillis)
            put("endTimeMillis", entry.endTimeMillis)
            put("userActivity", entry.userActivity)
            put("createdAt", entry.createdAt)
        }
    }

    private companion object {
        private const val PREF_NAME = "cosmos_time_skip_history_prefs"
        private const val KEY_ENTRIES = "time_skip_history_entries"
        private const val MAX_ENTRIES = 200
    }
}
