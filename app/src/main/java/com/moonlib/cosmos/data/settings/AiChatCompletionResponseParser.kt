package com.moonlib.cosmos.data.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI 兼容 Chat Completions 响应解析器。
 *
 * 职责单一：从不同服务商的 choices/message 结构中提取可展示的最终文本，并在空返回时给出可诊断内容。
 */
object AiChatCompletionResponseParser {

    fun extractContent(jsonText: String): String {
        val json = JSONObject(jsonText)
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""

        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.optJSONObject("message") ?: return ""
        val content = extractMessageContent(message)
        if (content.isNotBlank()) return content.trim()

        val finishReason = firstChoice.optString("finish_reason", "")
        val refusal = message.optString("refusal", "")
        val diagnostic = buildString {
            append("AI 返回了空 content")
            if (finishReason.isNotBlank()) append("，finish_reason=").append(finishReason)
            if (refusal.isNotBlank()) append("，refusal=").append(refusal.take(200))
            append("。原始响应：").append(jsonText.take(1600))
        }
        throw IllegalStateException(diagnostic)
    }

    private fun extractMessageContent(message: JSONObject): String {
        val rawContent = message.opt("content") ?: return ""
        return when (rawContent) {
            is String -> rawContent
            is JSONArray -> extractContentParts(rawContent)
            else -> ""
        }
    }

    private fun extractContentParts(parts: JSONArray): String {
        val builder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val text = part.optString("text", "")
            if (text.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(text)
            }
        }
        return builder.toString()
    }
}
