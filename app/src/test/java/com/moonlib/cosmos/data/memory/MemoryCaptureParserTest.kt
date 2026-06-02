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
        assertEquals("承诺", result.first().title)
        assertEquals(listOf("char_1"), result.first().characterIds)
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

        assertEquals(listOf("char_1"), result.first().characterIds)
    }

    private fun memoryJson(title: String, content: String, characterId: String?): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("content", content)
            put("character_ids", JSONArray().apply { characterId?.let { put(it) } })
            put("tags", JSONArray().put("关系"))
            put("importance", 2)
        }
    }
}
