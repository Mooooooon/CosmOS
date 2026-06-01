package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 提示词数据实体
 */
data class SystemPromptItem(
    val id: String,
    val title: String,
    val description: String,
    val content: String,
    val isSystem: Boolean = false
)

/**
 * 系统提示词持久化管理仓库
 * 职责单一：负责管理提示词配置的读取、修改、删除和预设。
 */
class SystemPromptRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "cosmos_system_prompt_prefs"
        private const val KEY_PROMPT_ITEMS = "system_prompt_items"
        
        const val ID_MAIN_PROMPT = "main_prompt"
        const val ID_WORLD_PROMPT = "world_prompt"
    }

    /**
     * 获取所有系统提示词项。如果列表为空，则进行初始化，预置主提示词。
     */
    fun getPromptItems(): List<SystemPromptItem> {
        val jsonString = prefs.getString(KEY_PROMPT_ITEMS, null)
        if (jsonString == null) {
            val defaultList = listOf(
                SystemPromptItem(
                    id = ID_MAIN_PROMPT,
                    title = "主提示词",
                    description = "会在所有的AI通讯时，拼接在最顶部。主要用于控制 AI 的全局角色扮演规则和交流规范限制。",
                    content = "【全局核心设定】\n1. 你现在处于 CosmOS 虚拟手机中，并在与用户进行仿真的即时通讯聊天。\n2. 请完全沉浸扮演角色，绝对不能出现 OOC 或脱离角色，永远不能承认你是人工智能模型或辅助助手。\n3. 不要使用任何 emoji 表情符号或颜文字，全部使用纯文本文字聊天。",
                    isSystem = true
                ),
                SystemPromptItem(
                    id = ID_WORLD_PROMPT,
                    title = "世界设定",
                    description = "用于存放世界观设定（如时代、地点、世界规则等）。会在所有 AI 通讯时，拼接到主提示词下方，人设提示词上方。",
                    content = "【世界观设定】\n1. 时代背景：现代都市。\n2. 科技水平：与现实当下同步。",
                    isSystem = true
                )
            )
            savePromptItems(defaultList)
            return defaultList
        }

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SystemPromptItem>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                list.add(
                    SystemPromptItem(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        description = json.getString("description"),
                        content = json.getString("content"),
                        isSystem = json.optBoolean("isSystem", false)
                    )
                )
            }
            
            // 确保主提示词必须存在于列表中（兜底策略）
            if (list.none { it.id == ID_MAIN_PROMPT }) {
                list.add(0, SystemPromptItem(
                    id = ID_MAIN_PROMPT,
                    title = "主提示词",
                    description = "会在所有的AI通讯时，拼接在最顶部。主要用于控制 AI 的全局角色扮演规则和交流规范限制。",
                    content = "【全局核心设定】\n1. 你现在处于 CosmOS 虚拟手机中，并在与用户进行仿真的即时通讯聊天。\n2. 请完全沉浸扮演角色，绝对不能出现 OOC 或脱离角色，永远不能承认你是人工智能模型或辅助助手。\n3. 不要使用任何 emoji 表情符号或颜文字，全部使用纯文本文字聊天。",
                    isSystem = true
                ))
                savePromptItems(list)
            }

            // 确保世界设定必须存在于列表中（兜底策略）
            if (list.none { it.id == ID_WORLD_PROMPT }) {
                val index = list.indexOfFirst { it.id == ID_MAIN_PROMPT }
                if (index != -1) {
                    list.add(index + 1, SystemPromptItem(
                        id = ID_WORLD_PROMPT,
                        title = "世界设定",
                        description = "用于存放世界观设定（如时代、地点、世界规则等）。会在所有 AI 通讯时，拼接到主提示词下方，人设提示词上方。",
                        content = "【世界观设定】\n1. 时代背景：现代都市。\n2. 科技水平：与现实当下同步。",
                        isSystem = true
                    ))
                } else {
                    list.add(SystemPromptItem(
                        id = ID_WORLD_PROMPT,
                        title = "世界设定",
                        description = "用于存放世界观设定（如时代、地点、世界规则等）。会在所有 AI 通讯时，拼接到主提示词下方，人设提示词上方。",
                        content = "【世界观设定】\n1. 时代背景：现代都市。\n2. 科技水平：与现实当下同步。",
                        isSystem = true
                    ))
                }
                savePromptItems(list)
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存或更新提示词项
     */
    fun savePromptItem(item: SystemPromptItem) {
        val currentList = getPromptItems().toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentList[index] = item
        } else {
            currentList.add(item)
        }
        savePromptItems(currentList)
    }

    /**
     * 删除指定的提示词项（系统预置项不能删除）
     */
    fun deletePromptItem(id: String) {
        val currentList = getPromptItems().toMutableList()
        val item = currentList.firstOrNull { it.id == id } ?: return
        if (item.isSystem) return // 系统预置的提示词不可被删除
        currentList.removeAll { it.id == id }
        savePromptItems(currentList)
    }

    /**
     * 获取主提示词的内容，用于拼接到通讯最顶部
     */
    fun getMainPromptContent(): String {
        return getPromptItems().firstOrNull { it.id == ID_MAIN_PROMPT }?.content ?: ""
    }

    /**
     * 获取世界设定的内容，用于拼接到系统提示词下方，人设提示词上方
     */
    fun getWorldPromptContent(): String {
        return getPromptItems().firstOrNull { it.id == ID_WORLD_PROMPT }?.content ?: ""
    }

    /**
     * 内部保存方法
     */
    private fun savePromptItems(list: List<SystemPromptItem>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val json = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("description", item.description)
                    put("content", item.content)
                    put("isSystem", item.isSystem)
                }
                jsonArray.put(json)
            }
            prefs.edit().putString(KEY_PROMPT_ITEMS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
