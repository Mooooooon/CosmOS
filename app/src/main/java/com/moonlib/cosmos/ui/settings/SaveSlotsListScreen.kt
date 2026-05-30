package com.moonlib.cosmos.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.SaveManager
import com.moonlib.cosmos.data.settings.SaveSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存档管理控制台主页面
 *
 * 职责单一：负责展示存档分区列表，并处理新建、切换、重命名和删除等高阶持久化交互操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSlotsListScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── 核心状态 ──────────────────────────────────────────────
    var slots by remember { mutableStateOf(SaveManager.getSaveSlots()) }
    var activeId by remember { mutableStateOf(SaveManager.getActiveSaveId()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTargetSlot by remember { mutableStateOf<SaveSlot?>(null) }
    var deleteTargetSlotId by remember { mutableStateOf<String?>(null) }
    var resetTargetSlotId by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }

    val refreshSlots = {
        slots = SaveManager.getSaveSlots()
        activeId = SaveManager.getActiveSaveId()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "存档管理",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            inputText = ""
                            showCreateDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建存档",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (slots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无存档数据",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(slots, key = { it.id }) { slot ->
                    val isActive = slot.id == activeId
                    SaveSlotCard(
                        slot = slot,
                        isActive = isActive,
                        onSelect = {
                            if (!isActive) {
                                SaveManager.switchSave(context, slot.id)
                                refreshSlots()
                            }
                        },
                        onRename = {
                            inputText = slot.name
                            renameTargetSlot = slot
                        },
                        onDelete = {
                            deleteTargetSlotId = slot.id
                        },
                        onReset = {
                            resetTargetSlotId = slot.id
                        }
                    )
                }
            }
        }
    }

    // ── 1. 新建存档对话框 ──────────────────────────────────────
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "新建存档",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("存档名称") },
                    placeholder = { Text("请输入新存档名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty()) {
                            SaveManager.createSave(trimmed)
                            refreshSlots()
                            showCreateDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── 2. 重命名存档对话框 ────────────────────────────────────
    val targetSlot = renameTargetSlot
    if (targetSlot != null) {
        AlertDialog(
            onDismissRequest = { renameTargetSlot = null },
            title = {
                Text(
                    text = "重命名存档",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("存档名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty()) {
                            SaveManager.renameSave(targetSlot.id, trimmed)
                            refreshSlots()
                            renameTargetSlot = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetSlot = null }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── 3. 删除确认对话框 ──────────────────────────────────────
    val deleteId = deleteTargetSlotId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetSlotId = null },
            title = {
                Text(
                    text = "删除存档",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "您确定要删除此存档吗？删除后，该存档的人设、聊天历史以及互动记录将被级联清理，且不可恢复！",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SaveManager.deleteSave(context, deleteId)
                        refreshSlots()
                        deleteTargetSlotId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确定删除", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetSlotId = null }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── 4. 重置确认对话框 ──────────────────────────────────────
    val resetId = resetTargetSlotId
    if (resetId != null) {
        val targetName = slots.firstOrNull { it.id == resetId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { resetTargetSlotId = null },
            title = {
                Text(
                    text = "重置存档",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "您确定要重置“${targetName}”吗？此操作将完整保留您创建的角色人设、联系人设定及个人昵称，但会彻底清空所有的聊天对话、实体互动历史、状态卡数值，并初始化虚拟时间！此操作不可撤销。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SaveManager.resetSave(context, resetId)
                        refreshSlots()
                        resetTargetSlotId = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确定重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetTargetSlotId = null }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * 单个存档卡片组件
 */
@Composable
private fun SaveSlotCard(
    slot: SaveSlot,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        label = "border_color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        label = "border_width"
    )

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE) }
    val formattedTime = remember(slot.lastModifiedAt) { dateFormat.format(Date(slot.lastModifiedAt)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .shadow(
                elevation = if (isActive) 3.dp else 1.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = slot.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (isActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "已载入",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "已装载",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "最后修改：$formattedTime",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 按钮操作区
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CompactIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "重命名",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    onClick = onRename
                )

                CompactIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "重置",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    onClick = onReset
                )

                // 默认存档和当前活跃存档禁止删除
                if (!isActive && slot.id != "default") {
                    CompactIconButton(
                        icon = Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

/**
 * 紧凑的图标按钮组件（固定 32.dp，极度节省横向空间）
 */
@Composable
private fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
