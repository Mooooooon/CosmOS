package com.moonlib.cosmos.data.memory

import com.moonlib.cosmos.data.settings.AiSceneType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCaptureParserTest {

    @Test
    fun parseIgnoresNonStoryScenes() {
        val json = JSONObject().put("memories", JSONArray().put(memoryJson("承诺", "她答应周末一起做饭。", "char_1")))

        val result = MemoryCaptureParser.parse(
            jsonObj = json,
            sceneType = AiSceneType.PROFILE_GENERATION,
            validCharacterIds = setOf("char_1")
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseFiltersInvalidAndBlankMemories() {
        val json = JSONObject().put(
            "memories",
            JSONArray()
                .put(memoryJson("承诺", "她答应周末一起做饭。", "char_1"))
                .put(memoryJson("", "没有标题。", "char_1"))
                .put(memoryJson("无效角色", "这条角色不存在。", "missing"))
        )

        val result = MemoryCaptureParser.parse(
            jsonObj = json,
            sceneType = AiSceneType.CHAT,
            validCharacterIds = setOf("char_1")
        )

        assertEquals(1, result.size)
        assertEquals("承诺", result.first().memory.title)
        assertEquals(listOf("char_1"), result.first().memory.characterIds)
    }

    @Test
    fun parseUsesFallbackCharacterWhenMissingIds() {
        val json = JSONObject().put("memories", JSONArray().put(memoryJson("口味", "你喜欢少糖的热拿铁。", null)))

        val result = MemoryCaptureParser.parse(
            jsonObj = json,
            sceneType = AiSceneType.INTERACTION,
            fallbackCharacterIds = listOf("char_1"),
            validCharacterIds = setOf("char_1")
        )

        assertEquals(listOf("char_1"), result.first().memory.characterIds)
    }

    @Test
    fun parseFiltersDisposableInteraction() {
        val json = JSONObject().put(
            "memories",
            JSONArray().put(memoryJson("刚刚回复", "她刚刚看着你笑了并回复了一句。", "char_1", tags = listOf("状态")))
        )

        val result = MemoryCaptureParser.parse(
            jsonObj = json,
            sceneType = AiSceneType.CHAT,
            validCharacterIds = setOf("char_1")
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseSupportsUpdateOperationAndTargetId() {
        val json = JSONObject().put(
            "memories",
            JSONArray().put(
                memoryJson(
                    title = "周末约定",
                    content = "她答应周末和你一起做饭，并约好由你准备食材。",
                    characterId = "char_1",
                    operation = "update",
                    targetId = "memory_1",
                    tags = listOf("约定")
                )
            )
        )

        val result = MemoryCaptureParser.parse(
            jsonObj = json,
            sceneType = AiSceneType.INTERACTION,
            validCharacterIds = setOf("char_1")
        )

        assertEquals("update", result.first().operation)
        assertEquals("memory_1", result.first().targetId)
        assertEquals("周末约定", result.first().memory.title)
    }

    @Test
    fun repositoryUpdatesExplicitTargetWithoutCreatingNewMemory() {
        val existing = MemoryEntry(
            id = "memory_1",
            title = "周末约定",
            content = "她答应周末和你一起做饭。",
            characterIds = listOf("char_1"),
            tags = listOf("约定"),
            createdAt = 100L,
            updatedAt = 100L
        )
        val update = MemoryCaptureParser.CaptureInstruction(
            operation = "update",
            targetId = "memory_1",
            memory = existing.copy(
                id = "new",
                content = "她答应周末和你一起做饭，并约好由你准备食材。",
                importance = 3
            )
        )

        val (merged, changed) = MemoryRepository.mergeCapturedMemories(
            currentMemories = listOf(existing),
            instructions = listOf(update),
            now = 200L
        )

        assertEquals(1, changed)
        assertEquals(1, merged.size)
        assertEquals("memory_1", merged.first().id)
        assertEquals(100L, merged.first().createdAt)
        assertEquals(200L, merged.first().updatedAt)
        assertEquals(3, merged.first().importance)
        assertTrue(merged.first().content.contains("准备食材"))
    }

    @Test
    fun repositoryMergesSimilarCreateLocally() {
        val existing = MemoryEntry(
            id = "memory_1",
            title = "周末约定",
            content = "她答应周末和你一起做饭。",
            characterIds = listOf("char_1"),
            tags = listOf("约定")
        )
        val create = MemoryCaptureParser.CaptureInstruction(
            operation = "create",
            targetId = null,
            memory = existing.copy(
                id = "memory_2",
                title = "周末做饭约定",
                content = "她答应周末和你一起做饭，并约好由你准备食材。",
                tags = listOf("约定", "共同经历")
            )
        )

        val (merged, changed) = MemoryRepository.mergeCapturedMemories(
            currentMemories = listOf(existing),
            instructions = listOf(create),
            now = 300L
        )

        assertEquals(1, changed)
        assertEquals(1, merged.size)
        assertEquals("memory_1", merged.first().id)
        assertTrue(merged.first().tags.contains("共同经历"))
    }

    @Test
    fun groupedListFormatIsCompact() {
        val text = MemoryContextFormatter.formatForGroupedList(
            memory = MemoryEntry(
                id = "memory_1",
                title = "周末约定",
                content = "她答应周末和你一起做饭。",
                characterIds = listOf("char_1"),
                tags = listOf("约定"),
                importance = 3
            ),
            timeText = "2026-06-03"
        )

        assertEquals("- id=memory_1 | 2026-06-03 | 周末约定：她答应周末和你一起做饭。", text)
    }

    private fun memoryJson(
        title: String,
        content: String,
        characterId: String?,
        operation: String? = null,
        targetId: String? = null,
        tags: List<String> = listOf("关系")
    ): JSONObject {
        return JSONObject().apply {
            operation?.let { put("operation", it) }
            targetId?.let { put("target_id", it) }
            put("title", title)
            put("content", content)
            put("character_ids", JSONArray().apply { characterId?.let { put(it) } })
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
            put("importance", 2)
        }
    }
}
