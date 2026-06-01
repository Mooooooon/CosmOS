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

        val httpResult = if (isNativeGenerateContent) {
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
        val rawResponse = httpResult.rawResponse

        AiRequestLogger.save(
            context = context,
            characterName = request.logCharacterName,
            modelName = activeProfile.modelName,
            userInput = request.logUserInput,
            aiResponse = rawResponse,
            prompt = prompt,
            requestDetails = httpResult.requestDetails
        )

        return AiSceneResult(
            rawResponse = rawResponse,
            cleanedJson = if (request.expectsJson) AiResponseCleaner.cleanJson(rawResponse) else AiResponseCleaner.removeThinking(rawResponse),
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
    ): AiHttpResult {
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
        val authHeader = if (serviceType == AiServiceType.VERTEX) AiAuthorizationHeader.create(serviceType, apiKey) else null
        authHeader?.let { conn.setRequestProperty("Authorization", it) }
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
        val requestDetails = buildRequestDetails(
            serviceType = serviceType,
            method = "POST",
            url = sanitizeUrl(urlStr),
            modelName = modelName,
            temperature = temperature,
            headers = buildMap {
                put("Content-Type", "application/json")
                authHeader?.let { put("Authorization", maskAuthorization(it)) }
            },
            body = requestJson
        )

        conn.outputStream.use { it.write(requestJson.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = conn.responseCode
        val responseText = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("AI 请求失败 HTTP $responseCode: $err")
        }
        val json = JSONObject(responseText)
        val candidates = json.optJSONArray("candidates") ?: return AiHttpResult("", requestDetails)
        if (candidates.length() == 0) return AiHttpResult("", requestDetails)
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return AiHttpResult("", requestDetails)
        val parts = content.optJSONArray("parts") ?: return AiHttpResult("", requestDetails)
        val builder = StringBuilder()
        for (i in 0 until parts.length()) {
            val text = parts.getJSONObject(i).optString("text", "")
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(text)
            }
        }
        return AiHttpResult(builder.toString(), requestDetails)
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
    ): AiHttpResult {
        val base = baseUrl.removeSuffix("/")
        val url = URL(if (base.endsWith("/chat/completions")) base else "$base/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.setRequestProperty("Content-Type", "application/json")
        val authHeader = AiAuthorizationHeader.create(serviceType, apiKey)
        conn.setRequestProperty("Authorization", authHeader)
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
        val requestDetails = buildRequestDetails(
            serviceType = serviceType,
            method = "POST",
            url = url.toString(),
            modelName = modelName,
            temperature = temperature,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to maskAuthorization(authHeader)
            ),
            body = requestJson
        )

        conn.outputStream.use { it.write(requestJson.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = conn.responseCode
        val responseText = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("AI 请求失败 HTTP $responseCode: $err")
        }
        return AiHttpResult(AiChatCompletionResponseParser.extractContent(responseText), requestDetails)
    }

    private data class AiHttpResult(
        val rawResponse: String,
        val requestDetails: String
    )

    private fun buildRequestDetails(
        serviceType: AiServiceType,
        method: String,
        url: String,
        modelName: String,
        temperature: Float,
        headers: Map<String, String>,
        body: JSONObject
    ): String {
        return JSONObject().apply {
            put("serviceType", serviceType.name)
            put("method", method)
            put("url", url)
            put("modelName", modelName)
            put("temperature", temperature.toDouble())
            put("headers", JSONObject(headers))
            put("body", body)
        }.toString(4)
    }

    private fun sanitizeUrl(url: String): String {
        return url.replace(Regex("([?&]key=)[^&]+")) { match ->
            "${match.groupValues[1]}***"
        }
    }

    private fun maskAuthorization(value: String): String {
        val prefix = value.substringBefore(" ", "")
        return if (prefix.isBlank()) "***" else "$prefix ***"
    }

    private fun JSONObject.copyWithoutSchemaName(): JSONObject {
        val result = JSONObject(this.toString())
        result.remove("_schema_name")
        return result
    }
}
