package com.moonlib.cosmos.ui.memory

import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.data.memory.MemoryEntry
import com.moonlib.cosmos.data.profile.CharacterProfile

/**
 * 记忆编辑弹窗。
 *
 * 职责单一：收集并校验单条记忆的编辑输入。
 */
@Composable
fun MemoryEditorDialog(
    initialMemory: MemoryEntry?,
    profiles: List<CharacterProfile>,
    onDismiss: () -> Unit,
    onSave: (MemoryEntry) -> Unit
) {
    var title by remember(initialMemory?.id) { mutableStateOf(initialMemory?.title ?: "") }
    var content by remember(initialMemory?.id) { mutableStateOf(initialMemory?.content ?: "") }
    var tagsText by remember(initialMemory?.id) { mutableStateOf(initialMemory?.tags?.joinToString("，") ?: "") }
    var selectedIds by remember(initialMemory?.id) { mutableStateOf(initialMemory?.characterIds?.toSet() ?: emptySet()) }
    var importance by remember(initialMemory?.id) { mutableStateOf((initialMemory?.importance ?: 1).toFloat()) }
    var isContextEnabled by remember(initialMemory?.id) { mutableStateOf(initialMemory?.isContextEnabled ?: true) }
    val canSave = title.trim().isNotBlank() && content.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialMemory == null) "新增记忆" else "编辑记忆", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签，用逗号分隔") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("关联角色", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                profiles.forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = profile.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + profile.id else selectedIds - profile.id
                            }
                        )
                        Text(profile.name)
                    }
                }
                Text("重要度 ${importance.roundToInt()}", color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 1f..3f,
                    steps = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isContextEnabled, onCheckedChange = { isContextEnabled = it })
                    Text("进入上下文")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        (initialMemory ?: MemoryEntry(title = title.trim(), content = content.trim())).copy(
                            title = title.trim(),
                            content = content.trim(),
                            characterIds = selectedIds.toList(),
                            tags = tagsText.split("，", ",").map { it.trim() }.filter { it.isNotBlank() },
                            importance = importance.roundToInt().coerceIn(1, 3),
                            sourceScene = initialMemory?.sourceScene ?: "manual",
                            createdAt = initialMemory?.createdAt ?: now,
                            updatedAt = now,
                            isContextEnabled = isContextEnabled
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
