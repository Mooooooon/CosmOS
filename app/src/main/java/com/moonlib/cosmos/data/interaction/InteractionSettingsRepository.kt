package com.moonlib.cosmos.data.interaction

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.moonlib.cosmos.data.settings.SaveManager

/**
 * 状态卡词条定义
 * @property name 词条名称 (如 "当前姿势")
 * @property description 词条解释 (如 "指角色当前的姿势、动作或物理位置...")
 */
data class StatusKey(
    val name: String,
    val description: String
)

/**
 * 实体互动设置与状态数据持久化仓库
 *
 * 职责单一：负责实体互动状态卡的开启状态、包含解释的词条列表、以及角色实时状态数据的 SharedPreferences 持久化管理。
 */
class InteractionSettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_interaction_settings_prefs"
        private const val KEY_STATUS_CARD_ENABLED = "status_card_enabled"
        private const val KEY_STATUS_KEYS = "status_keys"
        private const val PREFIX_CHARACTER_STATUS = "character_status_"

        // 默认词条及其详细释义，帮助 AI 准确理解更新方向
        val DEFAULT_KEYS = listOf(
            StatusKey(
                name = "当前姿势",
                description = "指角色当前的身体姿势、动作、神态或所处的具体物理位置（例如：站立、靠在墙边、坐在沙发上、面露羞涩地低下头等）"
            ),
            StatusKey(
                name = "当前服装",
                description = "指角色当前所穿着的衣物或服饰状态（例如：整洁的校服、略显凌乱的常服、轻便的运动装、宽松舒适的睡衣等）"
            )
        )
    }

    /**
     * 判断是否开启状态卡
     */
    fun isStatusCardEnabled(): Boolean {
        return prefs.getBoolean(KEY_STATUS_CARD_ENABLED, false)
    }

    /**
     * 开启/关闭状态卡
     */
    fun setStatusCardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STATUS_CARD_ENABLED, enabled).apply()
    }

    /**
     * 获取当前启用的词条列表
     */
    fun getStatusKeys(): List<StatusKey> {
        val jsonString = prefs.getString(KEY_STATUS_KEYS, null) ?: return DEFAULT_KEYS
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<StatusKey>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                list.add(
                    StatusKey(
                        name = jsonObj.getString("name"),
                        description = jsonObj.getString("description")
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            DEFAULT_KEYS
        }
    }

    /**
     * 保存启用的词条列表
     */
    fun setStatusKeys(keys: List<StatusKey>) {
        try {
            val jsonArray = JSONArray()
            for (key in keys) {
                jsonArray.put(JSONObject().apply {
                    put("name", key.name)
                    put("description", key.description)
                })
            }
            prefs.edit().putString(KEY_STATUS_KEYS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 恢复默认词条
     */
    fun restoreDefaultKeys() {
        setStatusKeys(DEFAULT_KEYS)
    }

    /**
     * 获取指定角色当前的实时状态（词条名 -> 状态值）
     */
    fun getCharacterStatus(characterId: String): Map<String, String> {
        val jsonString = prefs.getString(PREFIX_CHARACTER_STATUS + characterId, null) ?: return emptyMap()
        return try {
            val jsonObj = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObj.getString(key)
            }
            map
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * 更新指定角色的实时状态数据（合并更新，如果 AI 只更新了部分字段，其他保留）
     */
    fun updateCharacterStatus(characterId: String, statusMap: Map<String, String>) {
        val current = getCharacterStatus(characterId).toMutableMap()
        current.putAll(statusMap)
        saveCharacterStatus(characterId, current)
    }

    /**
     * 保存指定角色的完整实时状态数据
     */
    fun saveCharacterStatus(characterId: String, statusMap: Map<String, String>) {
        try {
            val jsonObj = JSONObject()
            for ((key, value) in statusMap) {
                jsonObj.put(key, value)
            }
            prefs.edit().putString(PREFIX_CHARACTER_STATUS + characterId, jsonObj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
