package com.moonlib.cosmos.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.moonlib.cosmos.data.memory.MemoryEntry
import com.moonlib.cosmos.data.memory.MemoryRepository
import com.moonlib.cosmos.data.profile.CharacterProfileRepository

/**
 * 记忆 App 顶层容器。
 *
 * 职责单一：协调记忆列表、详情与编辑弹窗的顶层状态。
 */
@Composable
fun MemoryAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val memoryRepo = remember { MemoryRepository(context) }
    val profileRepo = remember { CharacterProfileRepository(context) }
    val profiles = remember { profileRepo.getProfiles().filter { !it.isPlayer } }

    var memories by remember { mutableStateOf(memoryRepo.getMemories()) }
    var isEditorOpen by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var selectedMemory by remember { mutableStateOf<MemoryEntry?>(null) }

    fun refresh() {
        memories = memoryRepo.getMemories()
    }

    BackHandler(enabled = true) { onGoBack() }

    MemoryListScreen(
        memories = memories,
        profiles = profiles,
        onGoBack = onGoBack,
        onAddMemory = {
            editingMemory = null
            isEditorOpen = true
        },
        onEditMemory = {
            editingMemory = it
            isEditorOpen = true
        },
        onOpenMemory = { selectedMemory = it },
        onDeleteMemory = {
            memoryRepo.deleteMemory(it.id)
            if (selectedMemory?.id == it.id) selectedMemory = null
            refresh()
        },
        onToggleContext = { memory ->
            memoryRepo.upsertMemory(memory.copy(isContextEnabled = !memory.isContextEnabled))
            refresh()
        },
        modifier = modifier.fillMaxSize()
    )

    selectedMemory?.let { memory ->
        MemoryDetailDialog(
            memory = memory,
            profiles = profiles,
            onDismiss = { selectedMemory = null },
            onEdit = {
                selectedMemory = null
                editingMemory = memory
                isEditorOpen = true
            },
            onToggleContext = {
                memoryRepo.upsertMemory(memory.copy(isContextEnabled = !memory.isContextEnabled))
                selectedMemory = memoryRepo.getMemories().firstOrNull { it.id == memory.id }
                refresh()
            },
            onDelete = {
                memoryRepo.deleteMemory(memory.id)
                selectedMemory = null
                refresh()
            }
        )
    }

    if (isEditorOpen) {
        MemoryEditorDialog(
            initialMemory = editingMemory,
            profiles = profiles,
            onDismiss = {
                editingMemory = null
                isEditorOpen = false
            },
            onSave = { memory ->
                memoryRepo.upsertMemory(memory)
                editingMemory = null
                isEditorOpen = false
                refresh()
            }
        )
    }
}
