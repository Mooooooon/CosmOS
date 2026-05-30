package com.moonlib.cosmos.data.settings

/**
 * AI 场景类型枚举
 * 职责单一：标识不同的 AI 业务场景，以支持系统提示词及对话机制的场景特化与平滑扩展
 */
enum class AiSceneType {
    /**
     * 线上手机聊天场景
     */
    CHAT,

    /**
     * 线下实体面对面互动场景
     */
    INTERACTION
}
