package com.moonlib.cosmos.data.interaction

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.moonlib.cosmos.data.settings.SaveManager

/**
 * 线下互动数据持久化仓库
 *
 * 职责单一：负责单机环境下线下实体互动消息列表的读取、追加、删除及截断逻辑。
 */
class InteractionRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_interaction_prefs"
        private const val PREFIX_MESSAGES = "interaction_messages_"
        private const val KEY_LAST_MULTI_PARTICIPANT_IDS = "last_multi_participant_ids"
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
     * 保存一条共享互动消息到所有参与角色的互动记录。
     */
    fun saveSharedMessage(characterIds: List<String>, message: InteractionMessage) {
        val participants = characterIds.distinct()
        val sharedMessage = message.copy(participantIds = message.participantIds.ifEmpty { participants })
        for (characterId in participants) {
            saveMessage(characterId, sharedMessage)
        }
    }

    /**
     * 保存一组共享互动消息到所有参与角色的互动记录。
     */
    fun saveSharedMessages(characterIds: List<String>, messages: List<InteractionMessage>) {
        val participants = characterIds.distinct()
        for (message in messages) {
            saveSharedMessage(participants, message)
        }
    }

    fun getSharedMessagesForParticipants(characterIds: List<String>): List<InteractionMessage> {
        val selectedSet = characterIds.toSet()
        if (selectedSet.size < 2) return emptyList()
        return characterIds
            .flatMap { getMessages(it) }
            .distinctBy { it.id }
            .filter { it.participantIds.toSet() == selectedSet }
            .sortedBy { it.timestamp }
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
     * 删除共享互动消息。旧单人消息没有参与者时不执行跨角色删除。
     */
    fun deleteSharedMessage(message: InteractionMessage) {
        val participants = message.participantIds.distinct()
        if (participants.isEmpty()) return
        for (characterId in participants) {
            deleteMessage(characterId, message.id)
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

    /**
     * 从共享消息所在位置开始，同步截断所有参与角色的互动记录。
     */
    fun deleteSharedMessagesAfter(message: InteractionMessage) {
        val participants = message.participantIds.distinct()
        if (participants.isEmpty()) return
        for (characterId in participants) {
            deleteMessagesAfter(characterId, message.id)
        }
    }

    fun getLastMultiParticipantIds(): List<String> {
        val jsonString = prefs.getString(KEY_LAST_MULTI_PARTICIPANT_IDS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    add(jsonArray.getString(i))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun setLastMultiParticipantIds(characterIds: List<String>) {
        val jsonArray = JSONArray()
        characterIds.distinct().forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_LAST_MULTI_PARTICIPANT_IDS, jsonArray.toString()).apply()
    }

    // ─── JSON 编解码助手 ─────────────────────────────────────────

    private fun parseMessage(json: JSONObject): InteractionMessage {
        val statusMap = if (json.has("statusMap")) {
            val statusObj = json.getJSONObject("statusMap")
            val map = mutableMapOf<String, String>()
            val keys = statusObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = statusObj.getString(key)
            }
            map
        } else null

        val participantIds = if (json.has("participantIds")) {
            val jsonArray = json.getJSONArray("participantIds")
            buildList {
                for (i in 0 until jsonArray.length()) {
                    add(jsonArray.getString(i))
                }
            }
        } else {
            emptyList()
        }

        val statusMapByCharacterId = if (json.has("statusMapByCharacterId")) {
            val statusObj = json.getJSONObject("statusMapByCharacterId")
            val result = mutableMapOf<String, Map<String, String>>()
            val characterIds = statusObj.keys()
            while (characterIds.hasNext()) {
                val characterId = characterIds.next()
                val innerObj = statusObj.getJSONObject(characterId)
                val innerMap = mutableMapOf<String, String>()
                val keys = innerObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    innerMap[key] = innerObj.getString(key)
                }
                result[characterId] = innerMap
            }
            result
        } else null

        return InteractionMessage(
            id = json.getString("id"),
            senderId = json.getString("senderId"),
            content = json.getString("content"),
            timestamp = json.getLong("timestamp"),
            isPending = json.optBoolean("isPending", false),
            statusMap = statusMap,
            sceneId = json.optString("sceneId", "").ifBlank { null },
            participantIds = participantIds,
            statusMapByCharacterId = statusMapByCharacterId
        )
    }

    private fun serializeMessage(message: InteractionMessage): JSONObject {
        return JSONObject().apply {
            put("id", message.id)
            put("senderId", message.senderId)
            put("content", message.content)
            put("timestamp", message.timestamp)
            put("isPending", message.isPending)
            message.sceneId?.let { put("sceneId", it) }
            if (message.participantIds.isNotEmpty()) {
                val participantsArray = JSONArray()
                message.participantIds.distinct().forEach { participantsArray.put(it) }
                put("participantIds", participantsArray)
            }
            if (message.statusMap != null) {
                val statusObj = JSONObject()
                for ((key, value) in message.statusMap) {
                    statusObj.put(key, value)
                }
                put("statusMap", statusObj)
            }
            if (message.statusMapByCharacterId != null) {
                val statusObj = JSONObject()
                for ((characterId, innerMap) in message.statusMapByCharacterId) {
                    val innerObj = JSONObject()
                    for ((key, value) in innerMap) {
                        innerObj.put(key, value)
                    }
                    statusObj.put(characterId, innerObj)
                }
                put("statusMapByCharacterId", statusObj)
            }
        }
    }
}
