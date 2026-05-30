package com.moonlib.cosmos.data.diary

import android.content.Context
import android.content.SharedPreferences
import com.moonlib.cosmos.data.settings.SaveManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 日记数据存储仓库
 *
 * 职责单一：负责日记列表的 CRUD、人称（第一/第三人称）配置的持久化，完全隔离在当前存档槽位内。
 */
class DiaryRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_diary_prefs"
        private const val KEY_DIARIES = "diaries_list"
        private const val KEY_PERSPECTIVE = "diary_perspective" // "first" 或 "third"
    }

    /**
     * 获取所有日记列表，按创建时间戳升序排序
     */
    fun getDiaries(): List<DiaryEntry> {
        val jsonString = prefs.getString(KEY_DIARIES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<DiaryEntry>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                list.add(deserializeEntry(json))
            }
            list.sortedBy { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存完整的日记列表
     */
    fun saveDiaries(diaries: List<DiaryEntry>) {
        try {
            val jsonArray = JSONArray()
            for (entry in diaries) {
                jsonArray.put(serializeEntry(entry))
            }
            prefs.edit().putString(KEY_DIARIES, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 新增一篇日记
     */
    fun addDiary(entry: DiaryEntry) {
        val current = getDiaries().toMutableList()
        current.add(entry)
        saveDiaries(current)
    }

    /**
     * 删除单篇日记
     */
    fun deleteDiary(id: String) {
        val current = getDiaries().filter { it.id != id }
        saveDiaries(current)
    }

    /**
     * 清空所有日记
     */
    fun clearDiaries() {
        prefs.edit().remove(KEY_DIARIES).apply()
    }

    /**
     * 获取当前写作人称 (默认 "first" - 第一人称)
     */
    fun getPerspective(): String {
        return prefs.getString(KEY_PERSPECTIVE, "first") ?: "first"
    }

    /**
     * 设置写作人称 (支持 "first" 或 "third")
     */
    fun setPerspective(perspective: String) {
        prefs.edit().putString(KEY_PERSPECTIVE, perspective).apply()
    }

    // ─── JSON 辅助序列化/反序列化 ────────────────────────────────────

    private fun serializeEntry(entry: DiaryEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("timestamp", entry.timestamp)
            put("virtualTime", entry.virtualTime)
            put("playerInput", entry.playerInput)
            put("content", entry.content)
            put("summary", entry.summary)
            
            // 序列化 involvedCharacterIds
            val charIdsArray = JSONArray()
            entry.involvedCharacterIds.forEach { charIdsArray.put(it) }
            put("involvedCharacterIds", charIdsArray)

            // 序列化 statusMap (Map<String, Map<String, String>>)
            val statusMapObj = JSONObject()
            for ((charId, innerMap) in entry.statusMap) {
                val innerObj = JSONObject()
                for ((key, value) in innerMap) {
                    innerObj.put(key, value)
                }
                statusMapObj.put(charId, innerObj)
            }
            put("statusMap", statusMapObj)
        }
    }

    private fun deserializeEntry(json: JSONObject): DiaryEntry {
        val id = json.getString("id")
        val timestamp = json.getLong("timestamp")
        val virtualTime = json.getString("virtualTime")
        val playerInput = json.getString("playerInput")
        val content = json.getString("content")
        val summary = json.getString("summary")

        // 反序列化 involvedCharacterIds
        val charIdsArray = json.getJSONArray("involvedCharacterIds")
        val involvedCharacterIds = mutableListOf<String>()
        for (i in 0 until charIdsArray.length()) {
            involvedCharacterIds.add(charIdsArray.getString(i))
        }

        // 反序列化 statusMap
        val statusMap = mutableMapOf<String, Map<String, String>>()
        if (json.has("statusMap")) {
            val statusMapObj = json.getJSONObject("statusMap")
            val charIdsKeys = statusMapObj.keys()
            while (charIdsKeys.hasNext()) {
                val charId = charIdsKeys.next()
                val innerObj = statusMapObj.getJSONObject(charId)
                val innerMap = mutableMapOf<String, String>()
                val innerKeys = innerObj.keys()
                while (innerKeys.hasNext()) {
                    val key = innerKeys.next()
                    innerMap[key] = innerObj.getString(key)
                }
                statusMap[charId] = innerMap
            }
        }

        return DiaryEntry(
            id = id,
            timestamp = timestamp,
            virtualTime = virtualTime,
            playerInput = playerInput,
            content = content,
            summary = summary,
            involvedCharacterIds = involvedCharacterIds,
            statusMap = statusMap
        )
    }
}
