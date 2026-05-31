package com.moonlib.cosmos.data.ai

/**
 * AI 响应清洗器。
 *
 * 职责单一：从模型可能夹带的 Markdown、thinking 标签或解释文本中提取可解析 JSON。
 */
object AiResponseCleaner {

    fun cleanJson(rawResponse: String): String {
        var text = rawResponse.trim()
        text = removeThinking(text)
        if (text.startsWith("```")) {
            val firstLineEnd = text.indexOf('\n')
            if (firstLineEnd != -1) {
                text = text.substring(firstLineEnd + 1)
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length - 3)
            }
        }
        text = text.trim()

        val objectStart = text.indexOf('{')
        val objectEnd = text.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1).trim()
        }

        val arrayStart = text.indexOf('[')
        val arrayEnd = text.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1).trim()
        }

        return text
    }

    fun removeThinking(rawResponse: String): String {
        var text = rawResponse.trim()
        if (text.contains("</thinking>")) {
            text = text.substringAfterLast("</thinking>").trim()
        }
        if (text.contains("<thinking>")) {
            text = text.substringBefore("<thinking>").trim()
        }
        return text
    }
}
