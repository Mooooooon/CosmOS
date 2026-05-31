package com.moonlib.cosmos.data.ai

import android.content.Context
import com.moonlib.cosmos.data.settings.AiAuthorizationHeader
import com.moonlib.cosmos.data.settings.AiChatCompletionResponseParser
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiReasoningRequestOptions
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiVertexConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 统一 AI 请求客户端。
 */
object AiRequestClient {

    fun execute(context: Context, request: AiSceneRequest): AiSceneResult {
        val activeProfile = AiConfigRepository(context).getActiveProfile()
            ?: throw Exception("未检测到激活的 AI 模型。请前往【系统设置】配置模型服务。")

        if (activeProfile.apiKey.isBlank() || activeProfile.baseUrl.isBlank() || activeProfile.modelName.isBlank()) {
            throw Exception("激活的 AI 配置文件不完整，请前往【系统设置】检查。")
        }

        val prompt = AiPromptComposer.compose(request)
        val isNativeGenerateContent = activeProfile.serviceType == AiServiceType.VERTEX ||
            (activeProfile.serviceType == AiServiceType.GEMINI && activeProfile.baseUrl.contains("googleapis.com"))

        val rawResponse = if (isNativeGenerateContent) {
            executeGeminiOfficial(
                baseUrl = activeProfile.baseUrl,
                modelName = activeProfile.modelName,
                apiKey = activeProfile.apiKey,
                temperature = activeProfile.temperature,
                prompt = prompt,
                schema = request.responseSchema,
                expectsJson = request.expectsJson,
                serviceType = activeProfile.serviceType,
                vertexRegion = activeProfile.vertexRegion
            )
        } else {
            executeOpenAI(
                baseUrl = activeProfile.baseUrl,
                modelName = activeProfile.modelName,
                apiKey = activeProfile.apiKey,
                temperature = activeProfile.temperature,
                prompt = prompt,
                schema = request.responseSchema,
                expectsJson = request.expectsJson,
                serviceType = activeProfile.serviceType,
                thinkingLevel = activeProfile.thinkingLevel
            )
        }

        AiRequestLogger.save(
            context = context,
            characterName = request.logCharacterName,
            modelName = activeProfile.modelName,
            userInput = request.logUserInput,
            aiResponse = rawResponse,
            prompt = prompt
        )

        return AiSceneResult(
            rawResponse = rawResponse,
            cleanedJson = if (request.expectsJson) AiResponseCleaner.cleanJson(rawResponse) else rawResponse.trim(),
            promptForLog = prompt,
            modelName = activeProfile.modelName
        )
    }

    private fun executeGeminiOfficial(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        prompt: String,
        schema: JSONObject?,
        expectsJson: Boolean,
        serviceType: AiServiceType,
        vertexRegion: String
    ): String {
        val base = baseUrl.removeSuffix("/")
        val urlStr = if (serviceType == AiServiceType.VERTEX) {
            AiVertexConfig.buildGenerateContentUrl(apiKey, vertexRegion, modelName)
        } else {
            "$base/v1beta/models/$modelName:generateContent?key=$apiKey"
        }
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Content-Type", "application/json")
        if (serviceType == AiServiceType.VERTEX) {
            conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
        }
        conn.doOutput = true

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
                if (expectsJson) {
                    put("responseMimeType", "application/json")
                    schema?.let { put("responseSchema", it.copyWithoutSchemaName()) }
                }
            })
        }

        conn.outputStream.use { it.write(requestJson.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = conn.responseCode
        val responseText = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("AI 请求失败 HTTP $responseCode: $err")
        }
        val json = JSONObject(responseText)
        val candidates = json.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val builder = StringBuilder()
        for (i in 0 until parts.length()) {
            val text = parts.getJSONObject(i).optString("text", "")
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(text)
            }
        }
        return builder.toString()
    }

    private fun executeOpenAI(
        baseUrl: String,
        modelName: String,
        apiKey: String,
        temperature: Float,
        prompt: String,
        schema: JSONObject?,
        expectsJson: Boolean,
        serviceType: AiServiceType,
        thinkingLevel: String
    ): String {
        val base = baseUrl.removeSuffix("/")
        val url = URL(if (base.endsWith("/chat/completions")) base else "$base/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
        conn.doOutput = true

        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("temperature", temperature.toDouble())
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            AiReasoningRequestOptions.applyTo(this, serviceType, modelName, thinkingLevel)
            if (expectsJson) {
                val cleanedSchema = schema?.copyWithoutSchemaName()
                if (serviceType == AiServiceType.OPEN_AI && cleanedSchema != null) {
                    put("response_format", JSONObject().apply {
                        put("type", "json_schema")
                        put("json_schema", JSONObject().apply {
                            put("name", schema.optString("_schema_name", "cosmos_response"))
                            put("strict", false)
                            put("schema", cleanedSchema)
                        })
                    })
                } else {
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                }
            }
        }

        conn.outputStream.use { it.write(requestJson.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = conn.responseCode
        val responseText = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("AI 请求失败 HTTP $responseCode: $err")
        }
        return AiChatCompletionResponseParser.extractContent(responseText)
    }

    private fun JSONObject.copyWithoutSchemaName(): JSONObject {
        val result = JSONObject(this.toString())
        result.remove("_schema_name")
        return result
    }
}
