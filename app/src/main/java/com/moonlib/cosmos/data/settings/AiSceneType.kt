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
    INTERACTION,

    /**
     * 剧情日记生成场景
     */
    DIARY,

    /**
     * 推特评论盖楼场景
     */
    SOCIAL_REPLY_TWITTER,

    /**
     * 朋友圈评论盖楼场景
     */
    SOCIAL_REPLY_MOMENT,

    /**
     * 时间跳过期间的统一线上行为模拟场景
     */
    TIME_SKIP_ONLINE,

    /**
     * 角色档案生成场景
     */
    PROFILE_GENERATION,

    /**
     * 聊天联系人展示资料生成场景
     */
    CONTACT_PROFILE_GENERATION,

    /**
     * 推特博主主页资料生成场景
     */
    TWITTER_PROFILE_GENERATION,

    /**
     * 角色取名生成场景
     */
    CHARACTER_NAME_GENERATION
}
