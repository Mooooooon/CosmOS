package com.moonlib.cosmos.data.chat

import android.content.Context
import android.content.SharedPreferences
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.SaveManager
import com.moonlib.cosmos.data.twitter.TwitterProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 朋友圈动态独立持久化仓库
 * 
 * 职责单一：负责聊天 App 内朋友圈动态的数据存取、点赞持久化、角色动态 Profile 初始化与不同存档槽的物理隔离。
 */
class MomentRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_moment_prefs"
        private const val KEY_PROFILES = "moment_profiles"
        private const val KEY_MOMENTS = "moment_list"
        private const val KEY_LIKED_SET = "moment_liked_set"
    }

    init {
        // 自动初始化玩家自身的社交主页资料 (强制与 ChatRepository 里的昵称和头像同步)
        syncUserProfile()
    }

    /**
     * 与 ChatRepository 同步玩家昵称和头像
     */
    fun syncUserProfile() {
        val chatRepo = ChatRepository(context)
        val nickname = chatRepo.getUserNickname()
        val avatar = chatRepo.getUserAvatar()

        val myProfile = TwitterProfile(
            characterId = "user",
            nickname = nickname,
            username = "myself",
            avatar = avatar,
            bio = "这里是我的朋友圈个性签名~",
            isFollowed = true
        )
        saveProfile(myProfile)
    }

    /**
     * 获取所有保存的朋友圈资料
     */
    fun getProfiles(): List<TwitterProfile> {
        val chatRepo = ChatRepository(context)
        val contacts = chatRepo.getContacts()
        val currentList = prefs.getString(KEY_PROFILES, null)?.let { jsonString ->
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<TwitterProfile>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    list.add(parseProfile(jsonObject))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }
        } ?: mutableListOf()

        var changed = false
        val updatedList = currentList.toMutableList()

        for (contact in contacts) {
            val charId = contact.characterId
            if (charId.isBlank()) continue
            val existing = updatedList.firstOrNull { it.characterId == charId }
            if (existing == null) {
                val cleanName = contact.nickname
                val lowerUsername = cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "")
                val finalUsername = if (lowerUsername.isBlank()) "user_${charId.take(5)}" else "${lowerUsername}_chat"
                val npcProfile = TwitterProfile(
                    characterId = charId,
                    nickname = contact.nickname,
                    username = finalUsername,
                    avatar = contact.avatar,
                    bio = "这是 ${contact.nickname} 的朋友圈简介。",
                    isFollowed = true
                )
                updatedList.add(npcProfile)
                changed = true
            } else {
                if (existing.nickname != contact.nickname || existing.avatar != contact.avatar) {
                    val idx = updatedList.indexOf(existing)
                    updatedList[idx] = existing.copy(
                        nickname = contact.nickname,
                        avatar = contact.avatar
                    )
                    changed = true
                }
            }
        }

        if (changed) {
            saveProfilesList(updatedList)
        }
        return updatedList
    }

    /**
     * 获取单个角色的朋友圈设定资料
     */
    fun getProfile(characterId: String): TwitterProfile? {
        val list = getProfiles()
        val profile = list.firstOrNull { it.characterId == characterId }
        if (profile == null) {
            if (characterId == "user") {
                val chatRepo = ChatRepository(context)
                val defaultUser = TwitterProfile(
                    characterId = "user",
                    nickname = chatRepo.getUserNickname(),
                    username = "myself",
                    avatar = chatRepo.getUserAvatar(),
                    bio = "这里是我的朋友圈个性签名~",
                    isFollowed = true
                )
                saveProfile(defaultUser)
                return defaultUser
            } else {
                // 读取系统联系人档案，初始化默认资料
                val charProfileRepo = CharacterProfileRepository(context)
                val systemChar = charProfileRepo.getProfiles().firstOrNull { it.id == characterId }
                if (systemChar != null) {
                    val cleanName = systemChar.name
                    val lowerUsername = cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "")
                    val finalUsername = if (lowerUsername.isBlank()) "user_${characterId.take(5)}" else "${lowerUsername}_chat"
                    val npcProfile = TwitterProfile(
                        characterId = characterId,
                        nickname = systemChar.name,
                        username = finalUsername,
                        avatar = systemChar.avatar,
                        bio = "这是 ${systemChar.name} 的朋友圈简介。",
                        isFollowed = true
                    )
                    saveProfile(npcProfile)
                    return npcProfile
                }
            }
        }
        return profile
    }

    /**
     * 保存/更新朋友圈资料
     */
    fun saveProfile(profile: TwitterProfile) {
        val currentList = getProfiles().toMutableList()
        val index = currentList.indexOfFirst { it.characterId == profile.characterId }
        if (index != -1) {
            currentList[index] = profile
        } else {
            currentList.add(profile)
        }
        saveProfilesList(currentList)
    }

    /**
     * 获取所有保存的动态及回复
     */
    fun getMoments(): List<Moment> {
        val jsonString = prefs.getString(KEY_MOMENTS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<Moment>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(parseMoment(jsonObject))
            }
            // 按时间升序排序
            list.sortedBy { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取单条动态
     */
    fun getMoment(momentId: String): Moment? {
        return getMoments().firstOrNull { it.id == momentId }
    }

    /**
     * 保存单条动态/回复
     */
    fun saveMoment(moment: Moment) {
        val currentList = getMoments().toMutableList()
        val index = currentList.indexOfFirst { it.id == moment.id }
        if (index != -1) {
            currentList[index] = moment
        } else {
            currentList.add(moment)
        }
        saveMomentsList(currentList)
    }

    /**
     * 删除单条动态及其子评论回复
     */
    fun deleteMoment(momentId: String) {
        val currentList = getMoments().toMutableList()
        val toDelete = mutableSetOf<String>()

        fun collectChildIds(id: String) {
            toDelete.add(id)
            val children = currentList.filter { it.parentId == id }
            for (child in children) {
                collectChildIds(child.id)
            }
        }

        collectChildIds(momentId)
        currentList.removeAll { toDelete.contains(it.id) }
        saveMomentsList(currentList)

        // 清理点赞集合
        val likedSet = getLikedSet().toMutableSet()
        likedSet.removeAll(toDelete)
        saveLikedSet(likedSet)
    }

    /**
     * 获取某条动态的直接评论回复
     */
    fun getRepliesTo(momentId: String): List<Moment> {
        return getMoments().filter { it.parentId == momentId }.sortedBy { it.timestamp }
    }

    // ── 点赞持久化实现 ──────────────────────────────────────────

    private fun getLikedSet(): Set<String> {
        return prefs.getStringSet(KEY_LIKED_SET, emptySet()) ?: emptySet()
    }

    private fun saveLikedSet(set: Set<String>) {
        prefs.edit().putStringSet(KEY_LIKED_SET, set).apply()
    }

    fun isLiked(momentId: String): Boolean {
        return getLikedSet().contains(momentId)
    }

    fun toggleLike(momentId: String): Boolean {
        val current = getLikedSet().toMutableSet()
        val isNowLiked = if (current.contains(momentId)) {
            current.remove(momentId)
            false
        } else {
            current.add(momentId)
            true
        }
        saveLikedSet(current)
        return isNowLiked
    }

    // ── 内部序列化实现 ──────────────────────────────────────────

    private fun saveProfilesList(list: List<TwitterProfile>) {
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

    private fun saveMomentsList(list: List<Moment>) {
        try {
            val jsonArray = JSONArray()
            for (moment in list) {
                jsonArray.put(serializeMoment(moment))
            }
            prefs.edit().putString(KEY_MOMENTS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseProfile(json: JSONObject): TwitterProfile {
        return TwitterProfile(
            characterId = json.getString("characterId"),
            nickname = json.getString("nickname"),
            username = json.getString("username"),
            avatar = json.optString("avatar", ""),
            bio = json.optString("bio", ""),
            isFollowed = json.optBoolean("isFollowed", false)
        )
    }

    private fun serializeProfile(profile: TwitterProfile): JSONObject {
        return JSONObject().apply {
            put("characterId", profile.characterId)
            put("nickname", profile.nickname)
            put("username", profile.username)
            put("avatar", profile.avatar)
            put("bio", profile.bio)
            put("isFollowed", profile.isFollowed)
        }
    }

    private fun parseMoment(json: JSONObject): Moment {
        return Moment(
            id = json.getString("id"),
            authorId = json.getString("authorId"),
            content = json.getString("content"),
            imagePath = if (json.isNull("imagePath")) null else json.getString("imagePath"),
            videoPath = if (json.isNull("videoPath")) null else json.getString("videoPath"),
            timestamp = json.getLong("timestamp"),
            parentId = if (json.isNull("parentId")) null else json.getString("parentId"),
            replyToUsername = if (json.isNull("replyToUsername")) null else json.getString("replyToUsername")
        )
    }

    private fun serializeMoment(moment: Moment): JSONObject {
        return JSONObject().apply {
            put("id", moment.id)
            put("authorId", moment.authorId)
            put("content", moment.content)
            put("imagePath", moment.imagePath)
            put("videoPath", moment.videoPath)
            put("timestamp", moment.timestamp)
            put("parentId", moment.parentId)
            put("replyToUsername", moment.replyToUsername)
        }
    }
}
