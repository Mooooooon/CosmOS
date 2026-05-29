package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * AI 通讯日志实体
 */
data class AiLog(
    val id: String,
    val timestamp: Long,
    val characterName: String,
    val modelName: String,
    val userInput: String,
    val aiResponse: String,
    val prompt: String
)

/**
 * AI 通讯日志持久化仓库类
 * 职责单一：负责 AI 通讯日志的本地持久化读取、保存以及清空管理。
 */
class AiLogRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_ai_logs_prefs"
        private const val KEY_LOGS = "ai_logs"
        private const val MAX_LOGS_COUNT = 50
    }

    /**
     * 获取所有保存的 AI 通讯日志列表（按时间戳降序排列，最新在最前）
     */
    fun getLogs(): List<AiLog> {
        val jsonString = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<AiLog>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(parseLog(jsonObject))
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存一条新的 AI 通讯日志
     * 如果日志条数超过 [MAX_LOGS_COUNT] (50条)，则会自动移除最旧的一条日志。
     */
    fun saveLog(
        characterName: String,
        modelName: String,
        userInput: String,
        aiResponse: String,
        prompt: String
    ) {
        val currentLogs = getLogs().toMutableList()
        val newLog = AiLog(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            characterName = characterName,
            modelName = modelName,
            userInput = userInput,
            aiResponse = aiResponse,
            prompt = prompt
        )
        // 头部插入新日志
        currentLogs.add(0, newLog)

        // 截断多余的日志以防止持久化文件过大
        val finalLogs = if (currentLogs.size > MAX_LOGS_COUNT) {
            currentLogs.take(MAX_LOGS_COUNT)
        } else {
            currentLogs
        }

        saveList(finalLogs)
    }

    /**
     * 清空所有已保存的 AI 通讯日志
     */
    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    /**
     * 内部序列化列表保存
     */
    private fun saveList(list: List<AiLog>) {
        try {
            val jsonArray = JSONArray()
            for (log in list) {
                jsonArray.put(serializeLog(log))
            }
            prefs.edit().putString(KEY_LOGS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 内部解析 JSONObject 为实体类
     */
    private fun parseLog(json: JSONObject): AiLog {
        return AiLog(
            id = json.getString("id"),
            timestamp = json.getLong("timestamp"),
            characterName = json.getString("characterName"),
            modelName = json.getString("modelName"),
            userInput = json.getString("userInput"),
            aiResponse = json.getString("aiResponse"),
            prompt = json.getString("prompt")
        )
    }

    /**
     * 内部序列化实体类为 JSONObject
     */
    private fun serializeLog(log: AiLog): JSONObject {
        return JSONObject().apply {
            put("id", log.id)
            put("timestamp", log.timestamp)
            put("characterName", log.characterName)
            put("modelName", log.modelName)
            put("userInput", log.userInput)
            put("aiResponse", log.aiResponse)
            put("prompt", log.prompt)
        }
    }
}
