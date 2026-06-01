package com.moonlib.cosmos.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.VoiceOption
import com.moonlib.cosmos.data.settings.VoiceServiceClient
import com.moonlib.cosmos.data.settings.VoiceServiceRepository
import kotlinx.coroutines.launch

/**
 * 档案音色绑定区。
 *
 * 职责单一：展示当前绑定音色，并从已配置的语音服务中选择音色。
 */
@Composable
fun ProfileVoiceBindingSection(
    voiceId: String,
    voiceName: String,
    onVoiceSelected: (VoiceOption?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text("绑定音色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = if (voiceId.isBlank()) "未绑定，语音条将保持静默" else voiceName.ifBlank { voiceId },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDialog) {
        VoiceSelectDialog(
            selectedVoiceId = voiceId,
            onDismiss = { showDialog = false },
            onVoiceSelected = {
                onVoiceSelected(it)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelectDialog(
    selectedVoiceId: String,
    onDismiss: () -> Unit,
    onVoiceSelected: (VoiceOption?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VoiceServiceRepository(context) }
    var voices by remember { mutableStateOf(emptyList<VoiceOption>()) }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val loadVoices = {
        scope.launch {
            val profile = repository.getActiveProfile()
            if (profile == null) {
                error = "请先在设置中添加并激活语音服务。"
                return@launch
            }
            isLoading = true
            error = null
            try {
                voices = VoiceServiceClient.fetchVoices(profile)
            } catch (e: Exception) {
                error = e.message ?: "音色列表获取失败"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadVoices() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("选择音色", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("绑定后，对方发送语音条时会使用该音色播放。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索音色") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    isLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }

                    error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

                    else -> {
                        val filtered = voices.filter {
                            it.id.contains(query, ignoreCase = true) ||
                                it.name.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filtered.forEach { voice ->
                                VoiceOptionCard(
                                    voice = voice,
                                    selected = voice.id == selectedVoiceId,
                                    onClick = { onVoiceSelected(voice) }
                                )
                            }
                            if (filtered.isEmpty()) {
                                Text("没有匹配的音色", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { loadVoices() }) {
                Text("刷新")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onVoiceSelected(null) }) {
                    Text("清除绑定", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.size(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun VoiceOptionCard(
    voice: VoiceOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(voice.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(voice.id, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (voice.description.isNotBlank()) {
                Text(
                    voice.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
