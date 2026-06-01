package com.moonlib.cosmos.data.chat

import org.json.JSONObject

/**
 * 语音消息附加数据。
 *
 * 职责单一：在 ChatMessage.extra 中保存合成音频路径与时长。
 */
data class VoiceMessageExtra(
    val audioPath: String = "",
    val durationMillis: Long = 0L,
    val error: String = ""
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("audioPath", audioPath)
            put("durationMillis", durationMillis)
            put("error", error)
        }.toString()
    }

    companion object {
        fun parse(extra: String?): VoiceMessageExtra {
            if (extra.isNullOrBlank()) return VoiceMessageExtra()
            return try {
                val json = JSONObject(extra)
                VoiceMessageExtra(
                    audioPath = json.optString("audioPath", ""),
                    durationMillis = json.optLong("durationMillis", 0L),
                    error = json.optString("error", "")
                )
            } catch (e: Exception) {
                VoiceMessageExtra()
            }
        }
    }
}
