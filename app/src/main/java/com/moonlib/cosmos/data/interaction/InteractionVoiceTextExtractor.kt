package com.moonlib.cosmos.data.interaction

/**
 * 互动语音文本提取器。
 *
 * 职责单一：从实体互动消息中提取适合朗读的对白内容。
 */
object InteractionVoiceTextExtractor {

    private val actionRegex = """[（(][^）)]*[）)]""".toRegex()

    fun extractSpeech(content: String): String {
        val speech = content.replace(actionRegex, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return speech.ifBlank {
            content.replace(Regex("[（）()]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
