package com.moonlib.cosmos.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository

/**
 * 存档槽位数据结构
 */
data class SaveSlot(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastModifiedAt: Long
)

/**
 * 全局唯一的存档管理器
 * 职责单一：负责全局多个存档槽位的增删改查、当前激活存档状态切换、以及向后兼容的路径/配置分区路由。
 */
object SaveManager {
    private const val PREF_GLOBAL_NAME = "cosmos_global_save_prefs"
    private const val KEY_ACTIVE_SAVE_ID = "active_save_id"
    private const val KEY_SAVE_SLOTS = "save_slots"

    private lateinit var globalPrefs: SharedPreferences

    /**
     * 当前激活的存档 ID（利用 Compose 的 State，可以被 UI 层直接响应式监听）
     */
    val activeSaveIdState = mutableStateOf("default")

    /**
     * 初始化全局存档管理器
     */
    fun init(context: Context) {
        globalPrefs = context.applicationContext.getSharedPreferences(PREF_GLOBAL_NAME, Context.MODE_PRIVATE)
        val activeId = globalPrefs.getString(KEY_ACTIVE_SAVE_ID, "default") ?: "default"
        activeSaveIdState.value = activeId

        // 如果存档列表为空，初始化默认存档以兼容历史数据
        val slots = getSaveSlots()
        if (slots.isEmpty()) {
            val defaultSlot = SaveSlot(
                id = "default",
                name = "默认存档",
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis()
            )
            saveSaveSlots(listOf(defaultSlot))
        }
    }

    /**
     * 获取当前激活的存档 ID
     */
    fun getActiveSaveId(): String {
        return activeSaveIdState.value
    }

    /**
     * 获取当前激活的存档名称
     */
    fun getActiveSaveName(): String {
        val activeId = getActiveSaveId()
        return getSaveSlots().firstOrNull { it.id == activeId }?.name ?: "默认存档"
    }

    /**
     * 获取支持动态分区的 SharedPreferences 名字
     * 对于 "default" 默认存档，直接使用原始 baseName，保证老用户已有数据无缝兼容。
     * 对于新创建的存档，拼接 save_ 隔离标识。
     */
    fun getPrefName(baseName: String): String {
        val activeId = getActiveSaveId()
        return if (activeId == "default") {
            baseName
        } else {
            "cosmos_save_${activeId}_${baseName}"
        }
    }

    /**
     * 获取支持动态分区隔离的图片文件夹名称
     * 对于 "default" 默认存档，直接使用原始 baseDirName，保证向后兼容。
     * 对于新存档，使用 saves/{saveId}/baseDirName 路径。
     */
    fun getAvatarDirName(baseDirName: String): String {
        val activeId = getActiveSaveId()
        return if (activeId == "default") {
            baseDirName
        } else {
            "saves/$activeId/$baseDirName"
        }
    }

    /**
     * 获取所有可用的存档列表
     */
    fun getSaveSlots(): List<SaveSlot> {
        if (!::globalPrefs.isInitialized) return emptyList()
        val jsonString = globalPrefs.getString(KEY_SAVE_SLOTS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SaveSlot>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                list.add(
                    SaveSlot(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        createdAt = json.getLong("createdAt"),
                        lastModifiedAt = json.getLong("lastModifiedAt")
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 内部保存存档列表方法
     */
    private fun saveSaveSlots(slots: List<SaveSlot>) {
        if (!::globalPrefs.isInitialized) return
        try {
            val jsonArray = JSONArray()
            for (slot in slots) {
                jsonArray.put(JSONObject().apply {
                    put("id", slot.id)
                    put("name", slot.name)
                    put("createdAt", slot.createdAt)
                    put("lastModifiedAt", slot.lastModifiedAt)
                })
            }
            globalPrefs.edit().putString(KEY_SAVE_SLOTS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 切换当前激活的存档
     */
    fun switchSave(context: Context, saveId: String): Boolean {
        if (!::globalPrefs.isInitialized) return false
        val slots = getSaveSlots()
        if (slots.none { it.id == saveId }) return false

        // 更新最后修改时间
        val updatedSlots = slots.map {
            if (it.id == saveId) {
                it.copy(lastModifiedAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        saveSaveSlots(updatedSlots)

        // 写入本地并更新全局 State
        globalPrefs.edit().putString(KEY_ACTIVE_SAVE_ID, saveId).apply()
        activeSaveIdState.value = saveId

        // 重新初始化虚拟时间系统，重新读入新存档时间
        com.moonlib.cosmos.data.time.VirtualTimeManager.init(context)
        return true
    }

    /**
     * 创建一个全新的存档
     */
    fun createSave(name: String): SaveSlot? {
        if (!::globalPrefs.isInitialized) return null
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null

        val id = UUID.randomUUID().toString().replace("-", "")
        val newSlot = SaveSlot(
            id = id,
            name = cleanName,
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        val currentSlots = getSaveSlots().toMutableList()
        currentSlots.add(newSlot)
        saveSaveSlots(currentSlots)
        return newSlot
    }

    /**
     * 重命名指定的存档
     */
    fun renameSave(saveId: String, newName: String): Boolean {
        if (!::globalPrefs.isInitialized) return false
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false

        val slots = getSaveSlots().toMutableList()
        val index = slots.indexOfFirst { it.id == saveId }
        if (index == -1) return false

        slots[index] = slots[index].copy(
            name = cleanName,
            lastModifiedAt = System.currentTimeMillis()
        )
        saveSaveSlots(slots)
        return true
    }

    /**
     * 删除指定的存档
     * 限制：不能删除当前激活的存档，且至少保留默认存档
     */
    fun deleteSave(context: Context, saveId: String): Boolean {
        if (!::globalPrefs.isInitialized) return false
        if (saveId == getActiveSaveId()) return false // 无法删除当前正在使用的存档
        if (saveId == "default") return false // 默认存档禁止删除

        val slots = getSaveSlots().toMutableList()
        val index = slots.indexOfFirst { it.id == saveId }
        if (index == -1) return false

        slots.removeAt(index)
        saveSaveSlots(slots)

        // 级联清理该存档的文件目录（即 saves/{saveId}/ 下所有的头像等文件）
        try {
            val saveDir = java.io.File(context.filesDir, "saves/$saveId")
            if (saveDir.exists()) {
                saveDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 清除该存档对应的所有 SharedPreferences 文件以释放空间
        try {
            val sharedPrefsDir = java.io.File(context.filesDir.parentFile, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                val prefix = "cosmos_save_${saveId}_"
                val files = sharedPrefsDir.listFiles { _, name -> name.startsWith(prefix) }
                files?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    /**
     * 重置指定的存档
     * 保持设定（人设、联系人设定、个人昵称/头像），但清空所有聊天、线下互动记录与实时状态卡数据，并将时间初始化。
     */
    fun resetSave(context: Context, saveId: String): Boolean {
        if (!::globalPrefs.isInitialized) return false
        val slots = getSaveSlots()
        if (slots.none { it.id == saveId }) return false

        val previousActiveId = getActiveSaveId()

        try {
            // 临时切档以让所有的动态 SharedPreferences 代理到目标存档分区
            activeSaveIdState.value = saveId

            // 1. 清除联系人消息记录
            val chatRepo = ChatRepository(context)
            val contacts = chatRepo.getContacts()
            for (contact in contacts) {
                chatRepo.deleteMessages(contact.id)
            }

            // 2. 清除线下实体互动消息和实时状态
            val profileRepo = CharacterProfileRepository(context)
            val profiles = profileRepo.getProfiles()
            val interactionRepo = InteractionRepository(context)
            val interactionSettingsRepo = InteractionSettingsRepository(context)

            for (profile in profiles) {
                interactionRepo.deleteMessages(profile.id)
                interactionSettingsRepo.saveCharacterStatus(profile.id, emptyMap())
            }

            // 3. 将虚拟时间回滚并初始化为系统当前时间
            com.moonlib.cosmos.data.time.VirtualTimeManager.rollbackTime(System.currentTimeMillis())

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // 恢复回原本的活跃存档 ID
            activeSaveIdState.value = previousActiveId

            // 如果重置的正巧是当前正在装载的存档，重新初始化时间
            if (saveId == previousActiveId) {
                com.moonlib.cosmos.data.time.VirtualTimeManager.init(context)
            }
        }

        // 更新最后修改时间
        val updatedSlots = slots.map {
            if (it.id == saveId) {
                it.copy(lastModifiedAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        saveSaveSlots(updatedSlots)
        return true
    }
}
