package com.moonlib.cosmos.data.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 模型目录。
 *
 * 职责单一：提供各服务商推荐模型，并在线拉取服务商模型列表。
 */
object AiModelCatalog {

    suspend fun fetchOnline(
        serviceType: AiServiceType,
        apiKey: String,
        baseUrl: String,
        vertexRegion: String = AiVertexConfig.DEFAULT_REGION
    ): List<String> = withContext(Dispatchers.IO) {
        val isGeminiOfficial = serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")
        if (serviceType == AiServiceType.VERTEX) {
            return@withContext fetchVertexModels(apiKey)
        }

        val base = baseUrl.removeSuffix("/")
        val urlStr = if (isGeminiOfficial) {
            "$base/v1beta/models?key=$apiKey"
        } else {
            "$base/models"
        }

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 60000
            readTimeout = 60000
            if (!isGeminiOfficial) {
                setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
            }
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        if (conn.responseCode == 200) {
            return@withContext parseModels(
                jsonText = conn.inputStream.bufferedReader().use { it.readText() },
                isGeminiOfficial = isGeminiOfficial
            )
        }

        val errorText = runCatching {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }.getOrDefault("")
        val tip = when (conn.responseCode) {
            401 -> "API Key 无效或未授权"
            404 -> "端点路由错误 (404)"
            else -> "请求失败"
        }
        throw Exception("HTTP ${conn.responseCode}: $tip ${errorText.take(100)}")
    }

    fun recommendedModels(serviceType: AiServiceType): List<String> {
        return when (serviceType) {
            AiServiceType.OPEN_AI -> listOf("gpt-4o", "gpt-4o-mini", "o1-mini", "o1-preview", "gpt-4-turbo", "gpt-3.5-turbo")
            AiServiceType.DEEP_SEEK -> listOf("deepseek-chat", "deepseek-coder")
            AiServiceType.GEMINI -> listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-1.0-pro", "gemini-2.0-flash-exp")
            AiServiceType.VERTEX -> vertexRecommendModels()
            AiServiceType.MINIMAX -> listOf(
                "MiniMax-M3",
                "MiniMax-M2.7",
                "MiniMax-M2.7-highspeed",
                "MiniMax-M2.5",
                "MiniMax-M2.5-highspeed",
                "MiniMax-M2.1",
                "MiniMax-M2.1-highspeed",
                "MiniMax-M2"
            )
        }
    }

    private fun parseModels(jsonText: String, isGeminiOfficial: Boolean): List<String> {
        val models = mutableListOf<String>()
        val json = JSONObject(jsonText)

        if (isGeminiOfficial) {
            val modelsArray = json.optJSONArray("models")
            if (modelsArray != null) {
                for (i in 0 until modelsArray.length()) {
                    val fullName = modelsArray.getJSONObject(i).optString("name")
                    val shortName = fullName.substringAfter("models/")
                    if (shortName.isNotBlank()) models.add(shortName)
                }
            }
        } else {
            val dataArray = json.optJSONArray("data")
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val id = dataArray.getJSONObject(i).optString("id")
                    if (id.isNotBlank()) models.add(id)
                }
            }
        }

        return models.distinct().sorted()
    }

    private suspend fun fetchVertexModels(
        serviceAccountJson: String
    ): List<String> = withContext(Dispatchers.IO) {
        val conn = (URL("https://aiplatform.googleapis.com/v1beta1/publishers/google/models?listAllVersions=true&pageSize=200")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 60000
            readTimeout = 60000
            setRequestProperty("Authorization", AiAuthorizationHeader.create(AiServiceType.VERTEX, serviceAccountJson))
            setRequestProperty("Accept", "application/json")
        }

        if (conn.responseCode != 200) {
            val errorText = runCatching {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrDefault("")
            throw Exception(
                "Vertex 未返回模型列表 HTTP ${conn.responseCode}，请检查服务账号权限。${errorText.take(120)}"
            )
        }

        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val models = mutableListOf<String>()
        val array = json.optJSONArray("publisherModels") ?: json.optJSONArray("models")
        if (array != null) {
            for (i in 0 until array.length()) {
                val id = array.getJSONObject(i).optString("name").substringAfterLast("/")
                    .takeIf { it.isNotBlank() }
                if (id != null && id.startsWith("gemini-")) {
                    models.add(id)
                }
            }
        }
        if (models.isEmpty()) {
            throw Exception("Vertex /models 响应为空，已保留推荐列表")
        }
        models.distinct().sorted()
    }

    private fun vertexRecommendModels(): List<String> {
        return listOf(
            "gemini-3-pro-preview",
            "gemini-3-flash-preview",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash-image-preview",
            "gemini-2.0-flash-001",
            "gemini-2.0-flash-lite-001",
            "gemini-1.5-pro-002",
            "gemini-1.5-flash-002"
        )
    }
}
