package com.moonlib.cosmos.data.twitter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.SaveManager
import com.moonlib.cosmos.utils.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 推特持久化数据仓库
 * 
 * 职责单一：负责推特模块的数据存取、关注管理、头像保存以及不同存档槽的物理隔离。
 */
class TwitterRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_twitter_prefs"
        private const val KEY_PROFILES = "twitter_profiles"
        private const val KEY_TWEETS = "twitter_tweets"
    }

    init {
        // 自动初始化用户自身 Profile
        if (getProfile("user") == null) {
            val userProfile = TwitterProfile(
                characterId = "user",
                nickname = "玩家",
                username = "myself",
                avatar = "",
                bio = "这里是我的推特主页简介~",
                isFollowed = true
            )
            saveProfile(userProfile)
        }
    }

    /**
     * 获取所有保存的推特主页资料
     */
    fun getProfiles(): List<TwitterProfile> {
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<TwitterProfile>()
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
     * 获取单个推特主页资料
     */
    fun getProfile(characterId: String): TwitterProfile? {
        // 如果是 user，若不存在则使用兜底值
        val list = getProfiles()
        val profile = list.firstOrNull { it.characterId == characterId }
        if (profile == null && characterId == "user") {
            val defaultUser = TwitterProfile(
                characterId = "user",
                nickname = "玩家",
                username = "myself",
                avatar = "",
                bio = "这里是我的推特主页简介~",
                isFollowed = true
            )
            saveProfile(defaultUser)
            return defaultUser
        }
        return profile
    }

    /**
     * 保存/更新推特主页资料
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
     * 关注系统中的某个角色
     */
    fun followCharacter(characterId: String): TwitterProfile {
        val existing = getProfile(characterId)
        if (existing != null) {
            if (!existing.isFollowed) {
                val updated = existing.copy(isFollowed = true)
                saveProfile(updated)
                return updated
            }
            return existing
        }

        // 不存在，需要读取系统档案，初始化默认的推特资料
        val charProfileRepo = CharacterProfileRepository(context)
        val systemChar = charProfileRepo.getProfiles().firstOrNull { it.id == characterId }
        
        // 自动转换一个英文用户名
        val cleanName = systemChar?.name ?: "character"
        val lowerUsername = cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "")
        val finalUsername = if (lowerUsername.isBlank()) "user_${characterId.take(5)}" else "${lowerUsername}_cos"

        val defaultProfile = TwitterProfile(
            characterId = characterId,
            nickname = systemChar?.name ?: "未命名角色",
            username = finalUsername,
            avatar = systemChar?.avatar ?: "",
            bio = "这是 ${systemChar?.name ?: "我"} 的推特主页简介。",
            isFollowed = true
        )
        saveProfile(defaultProfile)
        return defaultProfile
    }

    /**
     * 取消关注某个角色
     */
    fun unfollowCharacter(characterId: String) {
        val existing = getProfile(characterId) ?: return
        val updated = existing.copy(isFollowed = false)
        saveProfile(updated)
    }

    /**
     * 获取所有已关注的 NPC 推特档案列表
     */
    fun getFollowedProfiles(): List<TwitterProfile> {
        return getProfiles().filter { it.isFollowed && it.characterId != "user" }
    }

    /**
     * 获取所有保存的推文与回复
     */
    fun getTweets(): List<Tweet> {
        val jsonString = prefs.getString(KEY_TWEETS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<Tweet>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(parseTweet(jsonObject))
            }
            // 升序排列
            list.sortedBy { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取单条推文
     */
    fun getTweet(tweetId: String): Tweet? {
        return getTweets().firstOrNull { it.id == tweetId }
    }

    /**
     * 保存单条推文/回复
     */
    fun saveTweet(tweet: Tweet) {
        val currentList = getTweets().toMutableList()
        val index = currentList.indexOfFirst { it.id == tweet.id }
        if (index != -1) {
            currentList[index] = tweet
        } else {
            currentList.add(tweet)
        }
        saveTweetsList(currentList)
    }

    /**
     * 删除单条推文及其所有子回复（递归清理）
     */
    fun deleteTweet(tweetId: String) {
        val currentList = getTweets().toMutableList()
        val toDelete = mutableSetOf<String>()
        
        fun collectChildIds(id: String) {
            toDelete.add(id)
            val children = currentList.filter { it.parentId == id }
            for (child in children) {
                collectChildIds(child.id)
            }
        }
        
        collectChildIds(tweetId)
        currentList.removeAll { toDelete.contains(it.id) }
        saveTweetsList(currentList)
    }

    /**
     * 获取针对某条推文/回复的直接子回复
     */
    fun getRepliesTo(tweetId: String): List<Tweet> {
        return getTweets().filter { it.parentId == tweetId }.sortedBy { it.timestamp }
    }

    /**
     * 将外部图片裁剪并复制到本地推特专用存储沙盒中
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
            val dir = File(context.filesDir, SaveManager.getAvatarDirName("twitter_avatars"))
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            // 清理对应 ID 的旧头像文件
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

    /**
     * 专门用于推文配图复制保存的函数
     */
    fun copyTweetImageToLocal(uriString: String): String {
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
            val dir = File(context.filesDir, SaveManager.getAvatarDirName("twitter_images"))
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "tweet_img_${UUID.randomUUID()}_${System.currentTimeMillis()}.jpg")
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

    private fun saveTweetsList(list: List<Tweet>) {
        try {
            val jsonArray = JSONArray()
            for (tweet in list) {
                jsonArray.put(serializeTweet(tweet))
            }
            prefs.edit().putString(KEY_TWEETS, jsonArray.toString()).apply()
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

    private fun parseTweet(json: JSONObject): Tweet {
        return Tweet(
            id = json.getString("id"),
            authorId = json.getString("authorId"),
            content = json.getString("content"),
            imagePath = if (json.isNull("imagePath")) null else json.getString("imagePath"),
            timestamp = json.getLong("timestamp"),
            parentId = if (json.isNull("parentId")) null else json.getString("parentId"),
            replyToUsername = if (json.isNull("replyToUsername")) null else json.getString("replyToUsername")
        )
    }

    private fun serializeTweet(tweet: Tweet): JSONObject {
        return JSONObject().apply {
            put("id", tweet.id)
            put("authorId", tweet.authorId)
            put("content", tweet.content)
            put("imagePath", tweet.imagePath)
            put("timestamp", tweet.timestamp)
            put("parentId", tweet.parentId)
            put("replyToUsername", tweet.replyToUsername)
        }
    }
}
