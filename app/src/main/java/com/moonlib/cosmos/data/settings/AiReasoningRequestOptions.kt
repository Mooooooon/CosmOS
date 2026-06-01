package com.moonlib.cosmos.data.settings

import org.json.JSONObject

/**
 * OpenAI 兼容接口的思考参数构造器。
 *
 * 职责单一：把应用内统一的 thinkingLevel 转换为不同模型/服务商实际接受的 JSON 参数。
 */
object AiReasoningRequestOptions {

    fun applyTo(
        requestJson: JSONObject,
        serviceType: AiServiceType,
        modelName: String,
        thinkingLevel: String
    ) {
        if (serviceType == AiServiceType.MINIMAX) return
        when (AiThinkingLevel.normalize(thinkingLevel)) {
            AiThinkingLevel.DEFAULT.value -> return
            AiThinkingLevel.NONE.value -> applyDisabledOptions(requestJson, serviceType, modelName)
            else -> applyEnabledOptions(requestJson, serviceType, modelName, AiThinkingLevel.normalize(thinkingLevel))
        }
    }

    fun supportedLevelsFor(serviceType: AiServiceType, modelName: String): List<AiThinkingLevel> {
        val normalizedModel = modelName.lowercase()
        return when {
            serviceType == AiServiceType.MINIMAX -> listOf(AiThinkingLevel.DEFAULT)

            serviceType == AiServiceType.GEMINI || normalizedModel.contains("gemini") -> listOf(
                AiThinkingLevel.DEFAULT,
                AiThinkingLevel.NONE,
                AiThinkingLevel.LOW,
                AiThinkingLevel.MEDIUM,
                AiThinkingLevel.HIGH,
                AiThinkingLevel.AUTO
            )

            isDeepSeekModel(serviceType, normalizedModel) -> listOf(
                AiThinkingLevel.DEFAULT,
                AiThinkingLevel.NONE,
                AiThinkingLevel.AUTO,
                AiThinkingLevel.HIGH
            )

            isOpenAiReasoningModel(normalizedModel) -> listOf(
                AiThinkingLevel.DEFAULT,
                AiThinkingLevel.NONE,
                AiThinkingLevel.MINIMAL,
                AiThinkingLevel.LOW,
                AiThinkingLevel.MEDIUM,
                AiThinkingLevel.HIGH,
                AiThinkingLevel.EXTRA_HIGH
            )

            else -> listOf(
                AiThinkingLevel.DEFAULT,
                AiThinkingLevel.NONE,
                AiThinkingLevel.LOW,
                AiThinkingLevel.MEDIUM,
                AiThinkingLevel.HIGH
            )
        }
    }

    private fun applyDisabledOptions(
        requestJson: JSONObject,
        serviceType: AiServiceType,
        modelName: String
    ) {
        val normalizedModel = modelName.lowercase()
        when {
            isDeepSeekModel(serviceType, normalizedModel) -> {
                requestJson.put("thinking", JSONObject().apply {
                    put("type", "disabled")
                })
            }

            normalizedModel.contains("qwen") -> {
                requestJson.put("enable_thinking", false)
            }

            normalizedModel.contains("gemini") -> {
                requestJson.put("extra_body", JSONObject().apply {
                    put("google", JSONObject().apply {
                        put("thinking_config", JSONObject().apply {
                            put("thinking_budget", 0)
                        })
                    })
                })
            }

            isOpenAiReasoningModel(normalizedModel) -> {
                requestJson.put("reasoning_effort", AiThinkingLevel.NONE.value)
            }
        }
    }

    private fun applyEnabledOptions(
        requestJson: JSONObject,
        serviceType: AiServiceType,
        modelName: String,
        thinkingLevel: String
    ) {
        val normalizedModel = modelName.lowercase()
        when {
            isDeepSeekModel(serviceType, normalizedModel) -> {
                requestJson.put("thinking", JSONObject().apply {
                    put("type", if (thinkingLevel == AiThinkingLevel.AUTO.value) "auto" else "enabled")
                })
                if (thinkingLevel != AiThinkingLevel.AUTO.value) {
                    requestJson.put("reasoning_effort", mapDeepSeekEffort(thinkingLevel))
                }
            }

            normalizedModel.contains("qwen") -> {
                requestJson.put("enable_thinking", true)
                requestJson.put("thinking_budget", thinkingBudgetFor(thinkingLevel))
            }

            normalizedModel.contains("gemini") -> {
                if (thinkingLevel == AiThinkingLevel.AUTO.value) {
                    requestJson.put("extra_body", JSONObject().apply {
                        put("google", JSONObject().apply {
                            put("thinking_config", JSONObject().apply {
                                put("thinking_budget", -1)
                                put("include_thoughts", true)
                            })
                        })
                    })
                } else {
                    requestJson.put("reasoning_effort", mapOpenAiEffort(thinkingLevel))
                }
            }

            else -> {
                requestJson.put("reasoning_effort", mapOpenAiEffort(thinkingLevel))
            }
        }
    }

    private fun isDeepSeekModel(serviceType: AiServiceType, normalizedModel: String): Boolean {
        return serviceType == AiServiceType.DEEP_SEEK || normalizedModel.contains("deepseek")
    }

    private fun isOpenAiReasoningModel(normalizedModel: String): Boolean {
        return normalizedModel.startsWith("o") ||
            normalizedModel.startsWith("gpt-5") ||
            normalizedModel.contains("reasoning") ||
            normalizedModel.contains("reasoner")
    }

    private fun mapOpenAiEffort(thinkingLevel: String): String {
        return when (thinkingLevel) {
            AiThinkingLevel.AUTO.value -> AiThinkingLevel.MEDIUM.value
            AiThinkingLevel.EXTRA_HIGH.value -> AiThinkingLevel.HIGH.value
            else -> thinkingLevel
        }
    }

    private fun mapDeepSeekEffort(thinkingLevel: String): String {
        return when (thinkingLevel) {
            AiThinkingLevel.EXTRA_HIGH.value -> "max"
            AiThinkingLevel.MINIMAL.value,
            AiThinkingLevel.LOW.value,
            AiThinkingLevel.MEDIUM.value -> AiThinkingLevel.HIGH.value
            else -> thinkingLevel
        }
    }

    private fun thinkingBudgetFor(thinkingLevel: String): Int {
        return when (thinkingLevel) {
            AiThinkingLevel.MINIMAL.value,
            AiThinkingLevel.LOW.value -> 1024
            AiThinkingLevel.MEDIUM.value -> 8192
            AiThinkingLevel.HIGH.value -> 16384
            AiThinkingLevel.EXTRA_HIGH.value -> 32768
            AiThinkingLevel.AUTO.value -> -1
            else -> 8192
        }
    }
}
