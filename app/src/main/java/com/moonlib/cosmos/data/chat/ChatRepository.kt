package com.moonlib.cosmos.data.chat

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 聊天持久化仓库类
 *
 * 职责单一：负责联系人、聊天记录、个人配置的持久化存储，以及图库图片拷贝的底层操作。
 */
class ChatRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_chat_prefs"
        private const val KEY_CONTACTS = "chat_contacts"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val PREFIX_MESSAGES = "chat_messages_"
    }

    // ─── 1. 用户自身资料管理 ────────────────────────────────────────

    fun getUserNickname(): String {
        return prefs.getString(KEY_USER_NICKNAME, "CosmOS 用户") ?: "CosmOS 用户"
    }

    fun getUserAvatar(): String {
        return prefs.getString(KEY_USER_AVATAR, "") ?: ""
    }

    fun saveUserProfile(nickname: String, avatarPath: String) {
        prefs.edit()
            .putString(KEY_USER_NICKNAME, nickname.trim())
            .putString(KEY_USER_AVATAR, avatarPath.trim())
            .apply()
    }

    // ─── 2. 联系人数据管理 ────────────────────────────────────────

    fun getContacts(): List<ChatContact> {
        val jsonString = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<ChatContact>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                list.add(parseContact(jsonObj))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveContact(contact: ChatContact) {
        val current = getContacts().toMutableList()
        val index = current.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            current[index] = contact
        } else {
            current.add(contact)
        }
        saveContactsList(current)
    }

    fun deleteContact(contactId: String) {
        val current = getContacts().toMutableList()
        val index = current.indexOfFirst { it.id == contactId }
        if (index != -1) {
            current.removeAt(index)
            saveContactsList(current)
        }
        // 同时清理对应的聊天历史
        deleteMessages(contactId)
    }

    private fun saveContactsList(list: List<ChatContact>) {
        try {
            val jsonArray = JSONArray()
            for (contact in list) {
                jsonArray.put(serializeContact(contact))
            }
            prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── 3. 消息记录管理 ────────────────────────────────────────

    fun getMessages(contactId: String): List<ChatMessage> {
        val jsonString = prefs.getString(PREFIX_MESSAGES + contactId, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<ChatMessage>()
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

    fun saveMessage(contactId: String, message: ChatMessage) {
        val current = getMessages(contactId).toMutableList()
        current.add(message)
        saveMessagesList(contactId, current)
    }

    fun saveMessagesList(contactId: String, list: List<ChatMessage>) {
        try {
            val jsonArray = JSONArray()
            for (msg in list) {
                jsonArray.put(serializeMessage(msg))
            }
            prefs.edit().putString(PREFIX_MESSAGES + contactId, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteMessages(contactId: String) {
        prefs.edit().remove(PREFIX_MESSAGES + contactId).apply()
    }

    fun deleteMessage(contactId: String, messageId: String) {
        val current = getMessages(contactId).toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index != -1) {
            current.removeAt(index)
            saveMessagesList(contactId, current)
        }
    }

    // ─── 4. 本地图片安全拷贝逻辑 ─────────────────────────────────────

    /**
     * 将从系统图库选择的 Uri 图片拷贝到应用的私有存储空间，防止 Uri 访问权限过期失效。
     * @param uriString 外部图片 Uri 字符串
     * @param destId 目标所有者（如联系人ID或"user"）
     * @return 拷贝后的本地绝对路径。如果拷贝失败，返回空字符串。
     */
    fun copyAvatarToLocal(uriString: String, destId: String): String {
        if (uriString.isBlank()) return ""
        if (!uriString.startsWith("content://") && !uriString.startsWith("file://")) {
            // 如果已经是绝对文件路径，无需二次拷贝
            if (File(uriString).exists()) {
                return uriString
            }
            return ""
        }
        return try {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return ""
            
            val dir = File(context.filesDir, "chat_avatars")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            // 清理对应 ID 先前持有的旧头像，保持磁盘整洁
            val oldFiles = dir.listFiles { _, name -> name.startsWith(destId + "_") }
            oldFiles?.forEach { it.delete() }

            val file = File(dir, "${destId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // ─── 5. JSON 解析助手 ─────────────────────────────────────────

    private fun parseContact(json: JSONObject): ChatContact {
        return ChatContact(
            id = json.getString("id"),
            nickname = json.getString("nickname"),
            avatar = json.optString("avatar", ""),
            signature = json.optString("signature", ""),
            characterId = json.optString("characterId", "")
        )
    }

    private fun serializeContact(contact: ChatContact): JSONObject {
        return JSONObject().apply {
            put("id", contact.id)
            put("nickname", contact.nickname)
            put("avatar", contact.avatar)
            put("signature", contact.signature)
            put("characterId", contact.characterId)
        }
    }

    private fun parseMessage(json: JSONObject): ChatMessage {
        return ChatMessage(
            id = json.getString("id"),
            senderId = json.getString("senderId"),
            content = json.getString("content"),
            timestamp = json.getLong("timestamp"),
            isPending = json.optBoolean("isPending", false)
        )
    }

    private fun serializeMessage(message: ChatMessage): JSONObject {
        return JSONObject().apply {
            put("id", message.id)
            put("senderId", message.senderId)
            put("content", message.content)
            put("timestamp", message.timestamp)
            put("isPending", message.isPending)
        }
    }
}
