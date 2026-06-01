package com.moonlib.cosmos.data.settings

/**
 * 语音服务商类型。
 *
 * 职责单一：描述一个语音服务渠道的默认端点与默认模型。
 */
enum class VoiceServiceType(
    val displayName: String,
    val defaultUrl: String,
    val defaultModel: String
) {
    MINIMAX("MiniMax", "https://api.minimaxi.com/v1", "speech-2.8-turbo")
}

/**
 * 语音服务配置。
 *
 * 职责单一：承载单个语音合成服务的连接参数。
 */
data class VoiceServiceProfile(
    val id: String,
    val name: String,
    val serviceType: VoiceServiceType,
    val apiKey: String,
    val baseUrl: String,
    val modelName: String,
    val isActive: Boolean = false
)

/**
 * 可绑定到角色档案的音色信息。
 */
data class VoiceOption(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = ""
)
