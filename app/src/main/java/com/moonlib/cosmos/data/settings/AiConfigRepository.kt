package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * AI 配置文件持久化仓库类
 * 职责单一：负责 AI 配置文件的增删改查及激活状态的管理。
 */
class AiConfigRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_ai_settings_prefs"
        private const val KEY_PROFILES = "ai_profiles"
    }

    /**
     * 获取所有保存的 AI 配置文件列表
     */
    fun getProfiles(): List<AiProfile> {
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<AiProfile>()
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
     * 保存或更新单个 AI 配置文件
     */
    fun saveProfile(profile: AiProfile) {
        val currentList = getProfiles().toMutableList()
        val index = currentList.indexOfFirst { it.id == profile.id }

        // 如果是全新添加，并且当前没有任何配置，则默认自动激活
        val shouldBeActive = if (currentList.isEmpty()) true else profile.isActive

        val finalProfile = profile.copy(isActive = shouldBeActive)

        if (index != -1) {
            currentList[index] = finalProfile
        } else {
            currentList.add(finalProfile)
        }

        // 如果该配置被设为激活，需要将其他配置文件的激活状态置为 false
        if (shouldBeActive) {
            for (i in currentList.indices) {
                if (currentList[i].id != finalProfile.id) {
                    currentList[i] = currentList[i].copy(isActive = false)
                }
            }
        }

        saveList(currentList)
    }

    /**
     * 删除指定的 AI 配置文件
     * 如果删除的是当前处于激活状态的配置，则会自动激活剩余配置中的第一个（如果存在）
     */
    fun deleteProfile(id: String) {
        val currentList = getProfiles().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        val wasActive = currentList[index].isActive
        currentList.removeAt(index)

        // 如果被删除的是激活的，且列表还不为空，则激活剩下的第一个
        if (wasActive && currentList.isNotEmpty()) {
            currentList[0] = currentList[0].copy(isActive = true)
        }

        saveList(currentList)
    }

    /**
     * 快速切换当前激活的 AI 模型配置文件
     */
    fun setActiveProfile(id: String) {
        val currentList = getProfiles().map {
            it.copy(isActive = it.id == id)
        }
        saveList(currentList)
    }

    /**
     * 获取当前激活的 AI 配置文件，如果没有配置则返回 null
     */
    fun getActiveProfile(): AiProfile? {
        return getProfiles().firstOrNull { it.isActive }
    }

    /**
     * 内部方法：将列表保存入 SharedPreferences
     */
    private fun saveList(list: List<AiProfile>) {
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
     * 内部方法：将 JSONObject 转换回 AiProfile 实体
     */
    private fun parseProfile(json: JSONObject): AiProfile {
        val serviceTypeStr = json.optString("serviceType", AiServiceType.OPEN_AI.name)
        val serviceType = try {
            AiServiceType.valueOf(serviceTypeStr)
        } catch (e: Exception) {
            AiServiceType.OPEN_AI
        }

        return AiProfile(
            id            = json.getString("id"),
            name          = json.getString("name"),
            serviceType   = serviceType,
            apiKey        = json.getString("apiKey"),
            baseUrl       = json.getString("baseUrl"),
            modelName     = json.getString("modelName"),
            temperature   = json.optDouble("temperature", 0.7).toFloat(),
            isActive      = json.optBoolean("isActive", false),
            thinkingLevel = json.optString("thinkingLevel", "off")
        )
    }

    /**
     * 内部方法：将 AiProfile 实体序列化为 JSONObject
     */
    private fun serializeProfile(profile: AiProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("serviceType", profile.serviceType.name)
            put("apiKey", profile.apiKey)
            put("baseUrl", profile.baseUrl)
            put("modelName", profile.modelName)
            put("temperature", profile.temperature.toDouble())
            put("isActive", profile.isActive)
            put("thinkingLevel", profile.thinkingLevel)
        }
    }
}
