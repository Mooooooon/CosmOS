package com.moonlib.cosmos.data.ai

import com.moonlib.cosmos.data.settings.AiSceneType
import org.json.JSONObject

/**
 * 统一 AI 请求中的历史消息来源。
 */
enum class AiHistorySource(val label: String) {
    CHAT("线上聊天"),
    INTERACTION("线下互动"),
    DIARY("剧情日记"),
    TWITTER("推特动态"),
    MOMENT("朋友圈动态"),
    MEMORY("长期记忆")
}

/**
 * 统一 AI 请求中的历史消息表达。
 */
data class AiHistoryItem(
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val source: AiHistorySource
)

/**
 * Prompt 固定段落。
 */
data class AiPromptSection(
    val title: String,
    val content: String
)

/**
 * 统一 AI 场景请求。
 */
data class AiSceneRequest(
    val sceneType: AiSceneType,
    val systemPrompt: String,
    val worldPrompt: String = "",
    val personaPrompt: String,
    val outputRequirement: String,
    val jsonStructure: String,
    val memoryText: String = "",
    val historyText: String = "",
    val statusCard: String = "",
    val userInput: String = "",
    val logCharacterName: String,
    val logUserInput: String = userInput,
    val responseSchema: JSONObject? = null,
    val expectsJson: Boolean = true
)

/**
 * 统一 AI 场景响应。
 */
data class AiSceneResult(
    val rawResponse: String,
    val cleanedJson: String,
    val promptForLog: String,
    val modelName: String
)
