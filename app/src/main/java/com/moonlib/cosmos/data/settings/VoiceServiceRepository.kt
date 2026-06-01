package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 语音服务配置仓库。
 *
 * 职责单一：负责语音服务配置的增删改查与激活状态管理。
 */
class VoiceServiceRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_voice_service_prefs"
        private const val KEY_PROFILES = "voice_service_profiles"
    }

    fun getProfiles(): List<VoiceServiceProfile> {
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            (0 until array.length()).map { parseProfile(array.getJSONObject(it)) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getActiveProfile(): VoiceServiceProfile? {
        return getProfiles().firstOrNull { it.isActive }
    }

    fun saveProfile(profile: VoiceServiceProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        val finalProfile = profile.copy(isActive = if (current.isEmpty()) true else profile.isActive)
        if (index >= 0) {
            current[index] = finalProfile
        } else {
            current.add(finalProfile)
        }
        if (finalProfile.isActive) {
            for (i in current.indices) {
                if (current[i].id != finalProfile.id) {
                    current[i] = current[i].copy(isActive = false)
                }
            }
        }
        saveList(current)
    }

    fun deleteProfile(id: String) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val wasActive = current[index].isActive
        current.removeAt(index)
        if (wasActive && current.isNotEmpty()) {
            current[0] = current[0].copy(isActive = true)
        }
        saveList(current)
    }

    fun setActiveProfile(id: String) {
        saveList(getProfiles().map { it.copy(isActive = it.id == id) })
    }

    private fun saveList(list: List<VoiceServiceProfile>) {
        val array = JSONArray()
        list.forEach { array.put(serializeProfile(it)) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun parseProfile(json: JSONObject): VoiceServiceProfile {
        val type = runCatching {
            VoiceServiceType.valueOf(json.optString("serviceType", VoiceServiceType.MINIMAX.name))
        }.getOrDefault(VoiceServiceType.MINIMAX)
        return VoiceServiceProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            serviceType = type,
            apiKey = json.getString("apiKey"),
            baseUrl = json.getString("baseUrl"),
            modelName = json.getString("modelName"),
            isActive = json.optBoolean("isActive", false)
        )
    }

    private fun serializeProfile(profile: VoiceServiceProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("serviceType", profile.serviceType.name)
            put("apiKey", profile.apiKey)
            put("baseUrl", profile.baseUrl)
            put("modelName", profile.modelName)
            put("isActive", profile.isActive)
        }
    }
}
