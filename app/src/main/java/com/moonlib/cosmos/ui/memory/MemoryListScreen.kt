package com.moonlib.cosmos.ui.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.memory.MemoryEntry
import com.moonlib.cosmos.data.profile.CharacterProfile

/**
 * 记忆列表页。
 *
 * 职责单一：展示、搜索、筛选和分发记忆条目的用户操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryListScreen(
    memories: List<MemoryEntry>,
    profiles: List<CharacterProfile>,
    onGoBack: () -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (MemoryEntry) -> Unit,
    onOpenMemory: (MemoryEntry) -> Unit,
    onDeleteMemory: (MemoryEntry) -> Unit,
    onToggleContext: (MemoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedCharacterId by remember { mutableStateOf<String?>(null) }
    val filtered = memories.filter { memory ->
        val matchesQuery = query.isBlank() ||
            memory.title.contains(query, ignoreCase = true) ||
            memory.content.contains(query, ignoreCase = true) ||
            memory.tags.any { it.contains(query, ignoreCase = true) }
        val matchesCharacter = selectedCharacterId == null || memory.characterIds.contains(selectedCharacterId)
        matchesQuery && matchesCharacter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("记忆", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onAddMemory) {
                        Icon(Icons.Default.Add, contentDescription = "新增")
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索记忆") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { selectedCharacterId = null },
                    label = { Text("全部") },
                    enabled = selectedCharacterId != null
                )
                profiles.forEach { profile ->
                    AssistChip(
                        onClick = {
                            selectedCharacterId = if (selectedCharacterId == profile.id) null else profile.id
                        },
                        label = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        enabled = selectedCharacterId != profile.id
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无记忆",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { memory ->
                        MemoryListItem(
                            memory = memory,
                            profiles = profiles,
                            onOpen = { onOpenMemory(memory) },
                            onEdit = { onEditMemory(memory) },
                            onDelete = { onDeleteMemory(memory) },
                            onToggleContext = { onToggleContext(memory) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryListItem(
    memory: MemoryEntry,
    profiles: List<CharacterProfile>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleContext: () -> Unit
) {
    val names = memory.characterIds.mapNotNull { id -> profiles.firstOrNull { it.id == id }?.name }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(memory.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        memory.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
                IconButton(onClick = onToggleContext) {
                    Icon(
                        if (memory.isContextEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "上下文引用"
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (names.ifEmpty { listOf("未关联角色") }).joinToString("、"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("重要度 ${memory.importance}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}
