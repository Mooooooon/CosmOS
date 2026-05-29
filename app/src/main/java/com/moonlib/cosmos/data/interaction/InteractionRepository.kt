package com.moonlib.cosmos.data.interaction

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 线下互动数据持久化仓库
 *
 * 职责单一：负责单机环境下线下实体互动消息列表的读取、追加、删除及截断逻辑。
 */
class InteractionRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_interaction_prefs"
        private const val PREFIX_MESSAGES = "interaction_messages_"
    }

    /**
     * 获取指定角色的实体互动历史记录
     */
    fun getMessages(characterId: String): List<InteractionMessage> {
        val jsonString = prefs.getString(PREFIX_MESSAGES + characterId, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<InteractionMessage>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                list.add(parseMessage(jsonObj))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存单条实体互动消息
     */
    fun saveMessage(characterId: String, message: InteractionMessage) {
        val current = getMessages(characterId).toMutableList()
        current.add(message)
        saveMessagesList(characterId, current)
    }

    /**
     * 保存完整的消息列表
     */
    fun saveMessagesList(characterId: String, list: List<InteractionMessage>) {
        try {
            val jsonArray = JSONArray()
            for (msg in list) {
                jsonArray.put(serializeMessage(msg))
            }
            prefs.edit().putString(PREFIX_MESSAGES + characterId, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 删除单条指定的消息
     */
    fun deleteMessage(characterId: String, messageId: String) {
        val current = getMessages(characterId).toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index != -1) {
            current.removeAt(index)
            saveMessagesList(characterId, current)
        }
    }

    /**
     * 清空指定角色的所有互动记录
     */
    fun deleteMessages(characterId: String) {
        prefs.edit().remove(PREFIX_MESSAGES + characterId).apply()
    }

    /**
     * 保留至指定消息，并清除其后的所有消息（用于重新发送/回滚逻辑）
     */
    fun deleteMessagesAfter(characterId: String, messageId: String) {
        val current = getMessages(characterId)
        val index = current.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val kept = current.subList(0, index + 1)
            saveMessagesList(characterId, kept)
        }
    }

    // ─── JSON 编解码助手 ─────────────────────────────────────────

    private fun parseMessage(json: JSONObject): InteractionMessage {
        return InteractionMessage(
            id = json.getString("id"),
            senderId = json.getString("senderId"),
            content = json.getString("content"),
            timestamp = json.getLong("timestamp"),
            isPending = json.optBoolean("isPending", false)
        )
    }

    private fun serializeMessage(message: InteractionMessage): JSONObject {
        return JSONObject().apply {
            put("id", message.id)
            put("senderId", message.senderId)
            put("content", message.content)
            put("timestamp", message.timestamp)
            put("isPending", message.isPending)
        }
    }
}
