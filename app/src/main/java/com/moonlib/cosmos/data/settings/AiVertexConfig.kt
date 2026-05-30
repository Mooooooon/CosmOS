package com.moonlib.cosmos.data.settings

import org.json.JSONObject

/**
 * Vertex AI 服务账号配置解析工具。
 *
 * 职责单一：从用户粘贴的完整服务账号 JSON 中提取项目，并构造 OpenAI 兼容端点。
 */
object AiVertexConfig {
    const val DEFAULT_REGION = "global"

    fun projectIdFrom(serviceAccountJson: String): String {
        return JSONObject(serviceAccountJson).getString("project_id")
    }

    fun buildNativeBaseUrl(serviceAccountJson: String, region: String): String {
        val projectId = projectIdFrom(serviceAccountJson)
        val normalizedRegion = normalizeRegion(region)
        val host = if (normalizedRegion == DEFAULT_REGION) {
            "aiplatform.googleapis.com"
        } else {
            "$normalizedRegion-aiplatform.googleapis.com"
        }
        return "https://$host/v1/projects/$projectId/locations/$normalizedRegion"
    }

    fun buildGenerateContentUrl(serviceAccountJson: String, region: String, modelName: String): String {
        val model = normalizeModelName(modelName)
        return "${buildNativeBaseUrl(serviceAccountJson, region)}/publishers/google/models/$model:generateContent"
    }

    fun normalizeModelName(modelName: String): String {
        return modelName.trim().removePrefix("google/")
    }

    fun normalizeRegion(region: String): String {
        return region.trim().ifBlank { DEFAULT_REGION }
    }
}
