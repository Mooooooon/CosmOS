package com.moonlib.cosmos.data.memory

import android.content.Context
import android.content.SharedPreferences
import com.moonlib.cosmos.data.settings.SaveManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记忆仓库。
 *
 * 职责单一：负责当前存档内长期记忆的增删改查、筛选与持久化。
 */
class MemoryRepository(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SaveManager.getPrefName(PREF_NAME), Context.MODE_PRIVATE)

    companion object {
        const val PREF_NAME = "cosmos_memory_prefs"
        private const val KEY_MEMORIES = "memory_entries"
    }

    fun getMemories(): List<MemoryEntry> {
        val jsonString = prefs.getString(KEY_MEMORIES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    add(deserialize(jsonArray.getJSONObject(i)))
                }
            }.sortedWith(compareByDescending<MemoryEntry> { it.importance }.thenByDescending { it.updatedAt })
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveMemories(memories: List<MemoryEntry>) {
        val jsonArray = JSONArray()
        memories.forEach { jsonArray.put(serialize(it)) }
        prefs.edit().putString(KEY_MEMORIES, jsonArray.toString()).apply()
    }

    fun upsertMemory(memory: MemoryEntry) {
        val now = System.currentTimeMillis()
        val current = getMemories().toMutableList()
        val index = current.indexOfFirst { it.id == memory.id }
        val normalized = memory.copy(
            title = memory.title.trim(),
            content = memory.content.trim(),
            characterIds = memory.characterIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            tags = memory.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            importance = memory.importance.coerceIn(1, 3),
            updatedAt = now
        )
        if (index >= 0) {
            current[index] = normalized
        } else {
            current.add(normalized.copy(createdAt = normalized.createdAt.takeIf { it > 0L } ?: now))
        }
        saveMemories(current)
    }

    fun addFromCapture(memories: List<MemoryEntry>): Int {
        if (memories.isEmpty()) return 0
        val current = getMemories().toMutableList()
        var added = 0
        for (memory in memories) {
            if (memory.title.isBlank() || memory.content.isBlank()) continue
            val normalized = memory.copy(
                title = memory.title.trim(),
                content = memory.content.trim(),
                characterIds = memory.characterIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                tags = memory.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                importance = memory.importance.coerceIn(1, 3)
            )
            if (current.none { it.isSameMemoryAs(normalized) }) {
                current.add(normalized)
                added++
            }
        }
        if (added > 0) saveMemories(current)
        return added
    }

    fun deleteMemory(id: String) {
        saveMemories(getMemories().filter { it.id != id })
    }

    fun clearMemories() {
        prefs.edit().remove(KEY_MEMORIES).apply()
    }

    fun search(query: String, characterId: String? = null): List<MemoryEntry> {
        val trimmed = query.trim()
        return getMemories().filter { memory ->
            val matchesCharacter = characterId.isNullOrBlank() || memory.characterIds.contains(characterId)
            val matchesQuery = trimmed.isBlank() ||
                memory.title.contains(trimmed, ignoreCase = true) ||
                memory.content.contains(trimmed, ignoreCase = true) ||
                memory.tags.any { it.contains(trimmed, ignoreCase = true) }
            matchesCharacter && matchesQuery
        }
    }

    private fun MemoryEntry.isSameMemoryAs(other: MemoryEntry): Boolean {
        return title.normalizedKey() == other.title.normalizedKey() &&
            content.normalizedKey() == other.content.normalizedKey() &&
            characterIds.sorted() == other.characterIds.sorted()
    }

    private fun String.normalizedKey(): String {
        return trim().lowercase().replace(Regex("\\s+"), "")
    }

    private fun serialize(memory: MemoryEntry): JSONObject {
        return JSONObject().apply {
            put("id", memory.id)
            put("title", memory.title)
            put("content", memory.content)
            put("characterIds", JSONArray().apply { memory.characterIds.forEach { put(it) } })
            put("tags", JSONArray().apply { memory.tags.forEach { put(it) } })
            put("importance", memory.importance)
            put("sourceScene", memory.sourceScene)
            put("createdAt", memory.createdAt)
            put("updatedAt", memory.updatedAt)
            put("isContextEnabled", memory.isContextEnabled)
        }
    }

    private fun deserialize(json: JSONObject): MemoryEntry {
        return MemoryEntry(
            id = json.getString("id"),
            title = json.optString("title", ""),
            content = json.optString("content", ""),
            characterIds = json.optJSONArray("characterIds").toStringList(),
            tags = json.optJSONArray("tags").toStringList(),
            importance = json.optInt("importance", 1).coerceIn(1, 3),
            sourceScene = json.optString("sourceScene", "manual"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            isContextEnabled = json.optBoolean("isContextEnabled", true)
        )
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
