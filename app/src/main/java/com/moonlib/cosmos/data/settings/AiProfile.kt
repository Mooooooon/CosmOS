package com.moonlib.cosmos.data.settings

/**
 * AI 服务商类型枚举
 * 提供默认的 API 端点 (Base URL) 以及推荐的默认模型名称
 */
enum class AiServiceType(
    val displayName: String,
    val defaultUrl: String,
    val defaultModel: String
) {
    OPEN_AI("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
    DEEP_SEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-1.5-flash"),
    VERTEX("Vertex", "https://aiplatform.googleapis.com/v1", "gemini-2.0-flash-001")
}

/**
 * AI 配置文件实体
 * 每一个 [AiProfile] 职责单一：作为单个 AI 模型服务配置的数据容器
 */
data class AiProfile(
    val id: String,
    val name: String,
    val serviceType: AiServiceType,
    val apiKey: String,
    val baseUrl: String,
    val modelName: String,
    val temperature: Float = 0.7f,
    val isActive: Boolean = false,
    val thinkingLevel: String = "default",
    val vertexRegion: String = "global"
)
