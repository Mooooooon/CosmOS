package com.moonlib.cosmos.data.memory

/**
 * 记忆上下文格式化器。
 *
 * 职责单一：提供长期记忆在 Prompt 与历史上下文中的表达方式。
 */
object MemoryContextFormatter {

    const val CAPTURE_REQUIREMENT: String = """
        【长期记忆沉淀】
        如果本轮剧情出现长期有效的信息，请在 JSON 外层输出 memories 数组；没有则输出空数组。
        只记录偏好、关系变化、承诺、重要共同经历、身份事实、情感转折等会影响后续相处的内容。
        普通寒暄、短暂情绪、一次性动作状态、临时位置不要写入记忆。
        每条记忆格式为 {"title":"简短标题","content":"值得长期记住的剧情事实","character_ids":["相关角色ID"],"tags":["关系"],"importance":1}。
        importance 只能为 1、2、3，数字越大越重要。
    """

    fun format(memory: MemoryEntry): String {
        val tags = if (memory.tags.isEmpty()) "" else " 标签：${memory.tags.joinToString("、")}"
        return "【${memory.title}】${memory.content}$tags"
    }

    fun formatForGroupedList(memory: MemoryEntry, timeText: String): String {
        val tags = if (memory.tags.isEmpty()) "" else "；标签：${memory.tags.joinToString("、")}"
        return "- [$timeText] ${memory.title}：${memory.content}；重要度：${memory.importance}$tags"
    }
}
