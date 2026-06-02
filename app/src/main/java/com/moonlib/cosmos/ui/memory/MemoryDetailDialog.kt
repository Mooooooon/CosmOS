package com.moonlib.cosmos.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.data.memory.MemoryEntry
import com.moonlib.cosmos.data.profile.CharacterProfile

/**
 * 记忆详情弹窗。
 *
 * 职责单一：展示单条记忆详情与条目级操作。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MemoryDetailDialog(
    memory: MemoryEntry,
    profiles: List<CharacterProfile>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleContext: () -> Unit,
    onDelete: () -> Unit
) {
    val names = memory.characterIds.mapNotNull { id -> profiles.firstOrNull { it.id == id }?.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(memory.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(memory.content, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "关联：${names.ifEmpty { listOf("未关联角色") }.joinToString("、")}",
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    memory.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }
                Text(
                    text = "重要度 ${memory.importance} · ${if (memory.isContextEnabled) "已进入上下文" else "未进入上下文"}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onToggleContext) {
                    Text(if (memory.isContextEnabled) "停用引用" else "启用引用")
                }
                TextButton(onClick = onEdit) { Text("编辑") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
