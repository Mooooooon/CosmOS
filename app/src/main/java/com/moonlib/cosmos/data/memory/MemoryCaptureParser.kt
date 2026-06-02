package com.moonlib.cosmos.data.memory

import android.content.Context
import com.moonlib.cosmos.data.settings.AiSceneType
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 记忆捕获解析器。
 *
 * 职责单一：从剧情类 AI JSON 响应中提取合法长期记忆，并交给仓库保存。
 */
object MemoryCaptureParser {

    private val captureScenes = setOf(
        AiSceneType.CHAT,
        AiSceneType.INTERACTION,
        AiSceneType.DIARY,
        AiSceneType.SOCIAL_REPLY_TWITTER,
        AiSceneType.SOCIAL_REPLY_MOMENT,
        AiSceneType.TIME_SKIP_ONLINE
    )

    fun captureFromResponse(
        context: Context,
        jsonObj: JSONObject,
        sceneType: AiSceneType,
        fallbackCharacterIds: List<String> = emptyList(),
        validCharacterIds: Set<String> = emptySet(),
        timestamp: Long = System.currentTimeMillis()
    ): Int {
        val entries = parse(
            jsonObj = jsonObj,
            sceneType = sceneType,
            fallbackCharacterIds = fallbackCharacterIds,
            validCharacterIds = validCharacterIds,
            timestamp = timestamp
        )
        return MemoryRepository(context).addFromCapture(entries)
    }

    fun parse(
        jsonObj: JSONObject,
        sceneType: AiSceneType,
        fallbackCharacterIds: List<String> = emptyList(),
        validCharacterIds: Set<String> = emptySet(),
        timestamp: Long = System.currentTimeMillis()
    ): List<MemoryEntry> {
        if (sceneType !in captureScenes) return emptyList()
        val array = jsonObj.optJSONArray("memories") ?: return emptyList()
        return parseArray(array, sceneType, fallbackCharacterIds, validCharacterIds, timestamp)
    }

    private fun parseArray(
        array: JSONArray,
        sceneType: AiSceneType,
        fallbackCharacterIds: List<String>,
        validCharacterIds: Set<String>,
        timestamp: Long
    ): List<MemoryEntry> {
        val fallback = fallbackCharacterIds.filterValid(validCharacterIds)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                val content = obj.optString("content", "").trim()
                if (title.isBlank() || content.isBlank()) continue

                val parsedCharacterIds = obj.optJSONArray("character_ids")
                    .toStringList()
                    .filterValid(validCharacterIds)
                val characterIds = parsedCharacterIds.ifEmpty { fallback }
                if (validCharacterIds.isNotEmpty() && characterIds.isEmpty()) continue

                add(
                    MemoryEntry(
                        title = title.take(60),
                        content = content.take(500),
                        characterIds = characterIds.distinct(),
                        tags = obj.optJSONArray("tags").toStringList().map { it.take(16) }.distinct().take(8),
                        importance = obj.optInt("importance", 1).coerceIn(1, 3),
                        sourceScene = sceneType.name,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        isContextEnabled = true
                    )
                )
            }
        }
    }

    private fun List<String>.filterValid(validCharacterIds: Set<String>): List<String> {
        val cleaned = map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return if (validCharacterIds.isEmpty()) cleaned else cleaned.filter { it in validCharacterIds }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
}
