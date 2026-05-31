package com.moonlib.cosmos.data.ai

import android.content.Context
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository
import com.moonlib.cosmos.data.profile.CharacterProfile
import org.json.JSONObject

/**
 * 状态卡更新工具。
 */
object AiStatusUpdater {

    fun cleanStatusValue(value: String): String {
        return value
            .replace("（", "")
            .replace("）", "")
            .replace("(", "")
            .replace(")", "")
            .trim()
    }

    fun updateSingleCharacter(context: Context, characterId: String, jsonObj: JSONObject) {
        val statusObj = jsonObj.optJSONObject("status") ?: return
        val statusMap = mutableMapOf<String, String>()
        val keys = statusObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (statusObj.isNull(key)) continue
            val value = cleanStatusValue(statusObj.optString(key, ""))
            if (value.isNotBlank() && value != "null") {
                statusMap[key] = value
            }
        }
        if (statusMap.isNotEmpty()) {
            InteractionSettingsRepository(context).updateCharacterStatus(characterId, statusMap)
        }
    }

    fun updateMultipleCharacters(
        context: Context,
        profiles: List<CharacterProfile>,
        jsonObj: JSONObject
    ): Map<String, Map<String, String>> {
        val statusObj = jsonObj.optJSONObject("status") ?: return emptyMap()
        val result = mutableMapOf<String, Map<String, String>>()
        val names = statusObj.keys()
        val repo = InteractionSettingsRepository(context)
        while (names.hasNext()) {
            val charName = names.next()
            val profile = profiles.firstOrNull { it.name == charName || it.id == charName } ?: continue
            val charStatusObj = statusObj.optJSONObject(charName) ?: continue
            val single = mutableMapOf<String, String>()
            val keys = charStatusObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (charStatusObj.isNull(key)) continue
                val value = cleanStatusValue(charStatusObj.optString(key, ""))
                if (value.isNotBlank() && value != "null") {
                    single[key] = value
                }
            }
            if (single.isNotEmpty()) {
                repo.updateCharacterStatus(profile.id, single)
                result[profile.id] = single
            }
        }
        return result
    }
}
