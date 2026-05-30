package com.moonlib.cosmos.data.settings

/**
 * AI 思考等级的统一值。
 *
 * 职责单一：定义应用内部可保存、可展示的思考等级，并兼容旧版本存档中的 off。
 */
enum class AiThinkingLevel(
    val value: String,
    val label: String
) {
    DEFAULT("default", "默认 (不指定)"),
    NONE("none", "关闭 (Off)"),
    MINIMAL("minimal", "微小 (Minimal)"),
    LOW("low", "低 (Low)"),
    MEDIUM("medium", "中 (Medium)"),
    HIGH("high", "高 (High)"),
    EXTRA_HIGH("xhigh", "极高 (Extra High)"),
    AUTO("auto", "自动 (Auto)");

    companion object {
        fun normalize(value: String): String {
            return when (value.trim().lowercase()) {
                "off" -> NONE.value
                "" -> DEFAULT.value
                else -> value.trim().lowercase()
            }
        }

        fun fromValue(value: String): AiThinkingLevel {
            val normalized = normalize(value)
            return entries.firstOrNull { it.value == normalized } ?: DEFAULT
        }
    }
}
