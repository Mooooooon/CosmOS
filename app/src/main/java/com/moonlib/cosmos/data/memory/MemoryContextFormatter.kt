package com.moonlib.cosmos.data.memory

/**
 * 记忆上下文格式化器。
 *
 * 职责单一：提供长期记忆在 Prompt 与历史上下文中的表达方式。
 */
object MemoryContextFormatter {

    const val CAPTURE_REQUIREMENT: String = """
        【长期记忆沉淀】
        绝大多数普通互动都必须返回 "memories": []。不要为了完成格式而写记忆。
        只有当本轮剧情出现会影响未来相处的事实时才写入：明确约定、关系变化、重要共同经历、长期偏好、身份事实、情感转折、稳定称呼或边界。
        判断标准：这条信息未来是否会改变角色的行为、称呼、承诺、关系、偏好或剧情连续性；如果不会，就不要记录。
        禁止记录：普通问候、单句回应、重复表达、临时动作、当场状态、短暂情绪、当前位置、无后续影响的流水细节。
        如果新信息属于【记忆列表】中已有同一事件或同一关系线，必须输出 operation="update" 和对应 target_id，并把 content 写成合并后的精简最终版本；不要新建相似记忆。
        只有无法归入已有记忆时才输出 operation="create"。
        每条记忆格式为 {"operation":"create 或 update","target_id":"更新已有记忆时填写","title":"简短标题","content":"合并后的精简记忆正文","character_ids":["相关角色ID"],"tags":["关系"],"importance":1}。
        importance 只能为 1、2、3，数字越大越重要。
    """

    fun format(memory: MemoryEntry): String {
        val tags = if (memory.tags.isEmpty()) "" else " 标签：${memory.tags.joinToString("、")}"
        return "【${memory.title}】${memory.content}$tags"
    }

    fun formatForGroupedList(memory: MemoryEntry, timeText: String): String {
        return "- id=${memory.id} | $timeText | ${memory.title}：${memory.content}"
    }
}
