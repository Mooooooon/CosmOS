package com.moonlib.cosmos.data.settings

import android.content.Context
import org.json.JSONArray

/**
 * 模型列表缓存仓库。
 *
 * 职责单一：按服务商和端点持久化在线获取到的模型列表，避免每次打开弹窗都重新请求。
 */
class ModelListCacheRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getModels(cacheKey: String): List<String>? {
        val raw = prefs.getString(cacheKey, null) ?: return null
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getString(index) }
        } catch (e: Exception) {
            null
        }
    }

    fun saveModels(cacheKey: String, models: List<String>) {
        val array = JSONArray()
        models.distinct().sorted().forEach { array.put(it) }
        prefs.edit().putString(cacheKey, array.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "cosmos_model_list_cache"
        private const val CACHE_VERSION = 2

        fun key(serviceType: AiServiceType, baseUrl: String, region: String): String {
            return "v$CACHE_VERSION|${serviceType.name}|${baseUrl.trim()}|${region.trim()}"
        }
    }
}
