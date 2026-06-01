package com.moonlib.cosmos.data.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 语音服务网络客户端。
 *
 * 职责单一：向语音服务商获取音色列表并执行短文本语音合成。
 */
object VoiceServiceClient {

    suspend fun fetchVoices(profile: VoiceServiceProfile): List<VoiceOption> = withContext(Dispatchers.IO) {
        when (profile.serviceType) {
            VoiceServiceType.MINIMAX -> fetchMiniMaxVoices(profile)
        }
    }

    suspend fun synthesizeToFile(
        context: Context,
        profile: VoiceServiceProfile,
        voiceId: String,
        text: String,
        messageId: String
    ): VoiceSynthesisResult = withContext(Dispatchers.IO) {
        when (profile.serviceType) {
            VoiceServiceType.MINIMAX -> synthesizeMiniMax(context, profile, voiceId, text, messageId)
        }
    }

    private fun fetchMiniMaxVoices(profile: VoiceServiceProfile): List<VoiceOption> {
        val conn = openJsonConnection(profile, "${profile.baseUrl.trimEnd('/')}/get_voice").apply {
            doOutput = true
        }
        conn.outputStream.use { stream ->
            stream.write(JSONObject().put("voice_type", "all").toString().toByteArray(Charsets.UTF_8))
        }
        val json = readJsonOrThrow(conn, "音色列表获取失败")
        val error = json.optJSONObject("base_resp")
        if ((error?.optInt("status_code", 0) ?: 0) != 0) {
            throw Exception(error?.optString("status_msg", "音色列表获取失败") ?: "音色列表获取失败")
        }
        return buildList {
            addVoiceArray(json.optJSONArray("system_voice"), "系统音色")
            addVoiceArray(json.optJSONArray("voice_cloning"), "复刻音色")
            addVoiceArray(json.optJSONArray("voice_generation"), "生成音色")
        }.distinctBy { it.id }
    }

    private fun MutableList<VoiceOption>.addVoiceArray(array: JSONArray?, category: String) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("voice_id")
            if (id.isBlank()) continue
            val description = obj.optJSONArray("description")?.let { descArray ->
                (0 until descArray.length()).joinToString(" ") { descArray.optString(it) }
            }.orEmpty()
            add(
                VoiceOption(
                    id = id,
                    name = obj.optString("voice_name").ifBlank { id },
                    description = description,
                    category = category
                )
            )
        }
    }

    private fun synthesizeMiniMax(
        context: Context,
        profile: VoiceServiceProfile,
        voiceId: String,
        text: String,
        messageId: String
    ): VoiceSynthesisResult {
        val conn = openJsonConnection(profile, "${profile.baseUrl.trimEnd('/')}/t2a_v2").apply {
            doOutput = true
        }
        val body = JSONObject().apply {
            put("model", profile.modelName)
            put("text", text.take(10000))
            put("stream", false)
            put("output_format", "hex")
            put("voice_setting", JSONObject().apply {
                put("voice_id", voiceId)
                put("speed", 1)
                put("vol", 1)
                put("pitch", 0)
            })
            put("audio_setting", JSONObject().apply {
                put("sample_rate", 32000)
                put("bitrate", 128000)
                put("format", "mp3")
                put("channel", 1)
            })
            put("subtitle_enable", false)
            put("aigc_watermark", false)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val json = readJsonOrThrow(conn, "语音生成失败")
        val baseResp = json.optJSONObject("base_resp")
        if ((baseResp?.optInt("status_code", 0) ?: 0) != 0) {
            throw Exception(baseResp?.optString("status_msg", "语音生成失败") ?: "语音生成失败")
        }
        val audioHex = json.optJSONObject("data")?.optString("audio").orEmpty()
        if (audioHex.isBlank()) throw Exception("语音服务未返回音频")
        val audioBytes = decodeHex(audioHex)
        val dir = File(context.filesDir, SaveManager.getAvatarDirName("voice_messages"))
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$messageId.mp3")
        file.writeBytes(audioBytes)
        val duration = json.optJSONObject("extra_info")?.optLong("audio_length", 0L) ?: 0L
        return VoiceSynthesisResult(file.absolutePath, duration)
    }

    private fun openJsonConnection(profile: VoiceServiceProfile, url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer ${profile.apiKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readJsonOrThrow(conn: HttpURLConnection, fallback: String): JSONObject {
        val code = conn.responseCode
        val text = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (code !in 200..299) {
            throw Exception("HTTP $code: $fallback ${text.take(120)}")
        }
        return JSONObject(text)
    }

    private fun decodeHex(hex: String): ByteArray {
        val clean = hex.trim()
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            bytes[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return bytes
    }
}

data class VoiceSynthesisResult(
    val audioPath: String,
    val durationMillis: Long
)
