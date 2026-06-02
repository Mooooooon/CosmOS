package com.moonlib.cosmos.ui.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.ui.chat.AvatarView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun formatInteractionContent(text: String, isUser: Boolean): androidx.compose.ui.text.AnnotatedString {
    val actionColor = if (isUser) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    }
    val speechColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    return buildAnnotatedString {
        var cursor = 0
        val regex = """[（(][^）)]*[）)]""".toRegex()
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                withStyle(SpanStyle(color = speechColor, fontWeight = FontWeight.Normal)) {
                    append(text.substring(cursor, start))
                }
            }
            withStyle(
                SpanStyle(
                    color = actionColor,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(text.substring(start, end))
            }
            cursor = end
        }

        if (cursor < text.length) {
            withStyle(SpanStyle(color = speechColor, fontWeight = FontWeight.Normal)) {
                append(text.substring(cursor))
            }
        }
    }
}

@Composable
fun TimeLabel(
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val timeStr = remember(timestamp) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    }
    Text(
        text = timeStr,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun UserInteractionRow(
    content: String,
    userNickname: String,
    userAvatar: String,
    onDelete: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showMenu = true })
                }
            ) {
                Text(
                    text = formatInteractionContent(content, isUser = true),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 22.sp
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("从这里重新互动", color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        showMenu = false
                        onResend()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }

        AvatarView(
            avatarPath = userAvatar,
            name = userNickname,
            size = 40.dp
        )
    }
}

@Composable
fun CharacterInteractionRow(
    messageId: String,
    content: String,
    characterName: String,
    characterAvatar: String,
    voicePlaybackState: InteractionVoicePlaybackState?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 48.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        AvatarView(
            avatarPath = characterAvatar,
            name = characterName,
            size = 40.dp
        )

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showMenu = true })
                }
            ) {
                Text(
                    text = formatInteractionContent(content, isUser = false),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 22.sp
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (voicePlaybackState?.isAvailable == true) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when {
                                    voicePlaybackState.isLoading -> "正在生成语音..."
                                    voicePlaybackState.isPlaying -> "停止播放"
                                    else -> voicePlaybackState.statusText ?: "播放语音"
                                },
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            voicePlaybackState.play(messageId, content)
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun SystemMessageRow(
    content: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showMenu = true })
                }
            ) {
                Text(
                    text = content,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
