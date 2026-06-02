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

    data class CaptureInstruction(
        val operation: String,
        val targetId: String?,
        val memory: MemoryEntry
    )

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
        val instructions = parse(
            jsonObj = jsonObj,
            sceneType = sceneType,
            fallbackCharacterIds = fallbackCharacterIds,
            validCharacterIds = validCharacterIds,
            timestamp = timestamp
        )
        return MemoryRepository(context).applyCaptureInstructions(instructions)
    }

    fun parse(
        jsonObj: JSONObject,
        sceneType: AiSceneType,
        fallbackCharacterIds: List<String> = emptyList(),
        validCharacterIds: Set<String> = emptySet(),
        timestamp: Long = System.currentTimeMillis()
    ): List<CaptureInstruction> {
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
    ): List<CaptureInstruction> {
        val fallback = fallbackCharacterIds.filterValid(validCharacterIds)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val operation = obj.optString("operation", "create")
                    .trim()
                    .lowercase()
                    .takeIf { it == "create" || it == "update" }
                    ?: "create"
                val targetId = obj.optString("target_id", "")
                    .trim()
                    .takeIf { it.isNotBlank() }
                val title = obj.optString("title", "").trim()
                val content = obj.optString("content", "").trim()
                if (!hasLongTermValue(title, content, obj.optJSONArray("tags").toStringList())) continue

                val parsedCharacterIds = obj.optJSONArray("character_ids")
                    .toStringList()
                    .filterValid(validCharacterIds)
                val characterIds = parsedCharacterIds.ifEmpty { fallback }
                if (validCharacterIds.isNotEmpty() && characterIds.isEmpty()) continue

                val memory = MemoryEntry(
                    title = title.take(60),
                    content = content.take(240),
                    characterIds = characterIds.distinct(),
                    tags = obj.optJSONArray("tags").toStringList().map { it.take(16) }.distinct().take(5),
                    importance = obj.optInt("importance", 1).coerceIn(1, 3),
                    sourceScene = sceneType.name,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    isContextEnabled = true
                )
                add(CaptureInstruction(operation = operation, targetId = targetId, memory = memory))
            }
        }
    }

    private fun hasLongTermValue(title: String, content: String, tags: List<String>): Boolean {
        if (title.length < 2 || content.length < 10) return false
        if (content.length > 240) return false

        val joined = "$title $content ${tags.joinToString(" ")}"
        val longTermSignals = listOf(
            "约定", "承诺", "答应", "计划", "长期", "未来", "以后", "下次",
            "关系", "信任", "亲密", "疏远", "和解", "告白", "称呼",
            "偏好", "喜欢", "讨厌", "习惯", "害怕", "在意",
            "共同经历", "重要经历", "秘密", "身份", "目标", "边界", "转折"
        )
        val disposableSignals = listOf(
            "普通问候", "寒暄", "单句", "刚刚", "此刻", "现在", "临时",
            "看着", "笑了", "回复", "说了一句", "问了一句", "动作", "状态"
        )
        val hasLongTermSignal = longTermSignals.any { joined.contains(it) }
        val isClearlyDisposable = disposableSignals.any { joined.contains(it) }
        return hasLongTermSignal || !isClearlyDisposable && tags.any { tag ->
            listOf("关系", "约定", "承诺", "偏好", "经历", "身份", "称呼").any { tag.contains(it) }
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
