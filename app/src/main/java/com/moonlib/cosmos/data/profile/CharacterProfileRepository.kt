package com.moonlib.cosmos.data.profile

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.moonlib.cosmos.utils.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用户与角色人设持久化仓库类
 * 
 * 职责单一：负责对人设进行增删改查及保证“用户设定唯一性”的业务逻辑。
 */
class CharacterProfileRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_character_profiles_prefs"
        private const val KEY_PROFILES = "character_profiles"
    }

    /**
     * 获取所有保存的人设列表
     */
    fun getProfiles(): List<CharacterProfile> {
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<CharacterProfile>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(parseProfile(jsonObject))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存或更新单个人设配置
     * 强一致性逻辑：如果当前人设被设为了“用户设定 (isPlayer == true)”，
     * 则会自动将其他所有保存的人设的 isPlayer 置为 false，确保“全局有且仅有唯一的用户设定”。
     */
    fun saveProfile(profile: CharacterProfile) {
        val currentList = getProfiles().toMutableList()
        val index = currentList.indexOfFirst { it.id == profile.id }

        val finalProfile = profile

        if (index != -1) {
            currentList[index] = finalProfile
        } else {
            currentList.add(finalProfile)
        }

        // 唯一用户人设逻辑：若当前保存的设为了用户设定，则剥夺其他设定的用户身份
        if (finalProfile.isPlayer) {
            for (i in currentList.indices) {
                if (currentList[i].id != finalProfile.id && currentList[i].isPlayer) {
                    currentList[i] = currentList[i].copy(isPlayer = false)
                }
            }
        }

        saveList(currentList)
    }

    /**
     * 删除指定的人设配置
     */
    fun deleteProfile(id: String) {
        val currentList = getProfiles().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        currentList.removeAt(index)
        saveList(currentList)
    }

    /**
     * 内部持久化方法
     */
    private fun saveList(list: List<CharacterProfile>) {
        try {
            val jsonArray = JSONArray()
            for (profile in list) {
                jsonArray.put(serializeProfile(profile))
            }
            prefs.edit().putString(KEY_PROFILES, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 将从系统图库选择的 Uri 头像经过居中正方形裁剪和大图缩小后，拷贝到应用的私有存储空间
     * @param uriString 外部图片 Uri 字符串
     * @param destId 目标人设 ID
     * @return 拷贝处理后的本地绝对路径。如果拷贝失败，返回空字符串。
     */
    fun copyAvatarToLocal(uriString: String, destId: String): String {
        if (uriString.isBlank()) return ""
        val uri = if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            Uri.parse(uriString)
        } else {
            val file = File(uriString)
            if (file.exists()) {
                Uri.fromFile(file)
            } else {
                return ""
            }
        }
        return try {
            val dir = File(context.filesDir, "profile_avatars")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            // 清理对应 ID 先前持有的旧人设头像
            val oldFiles = dir.listFiles { _, name -> name.startsWith(destId + "_") }
            oldFiles?.forEach { it.delete() }

            val file = File(dir, "${destId}_${System.currentTimeMillis()}.jpg")
            val success = ImageUtils.processAndSaveAvatar(context, uri, file)
            if (success) {
                file.absolutePath
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseProfile(json: JSONObject): CharacterProfile {
        return CharacterProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            prompt = json.getString("prompt"),
            isPlayer = json.optBoolean("isPlayer", false),
            avatar = json.optString("avatar", "")
        )
    }

    private fun serializeProfile(profile: CharacterProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("prompt", profile.prompt)
            put("isPlayer", profile.isPlayer)
            put("avatar", profile.avatar)
        }
    }
}
