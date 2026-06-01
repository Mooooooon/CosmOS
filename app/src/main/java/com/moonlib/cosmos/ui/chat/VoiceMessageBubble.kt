package com.moonlib.cosmos.ui.chat

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.VoiceMessageExtra
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.VoiceServiceClient
import com.moonlib.cosmos.data.settings.VoiceServiceRepository
import java.io.File
import kotlinx.coroutines.launch

/**
 * 可播放语音消息气泡。
 *
 * 职责单一：为单条 voice 消息生成音频缓存并控制本地播放。
 */
@Composable
fun VoiceMessageBubble(
    msg: ChatMessage,
    isUser: Boolean,
    contactCharacterId: String,
    onUpdateMessage: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }
    val extra = VoiceMessageExtra.parse(msg.extra)
    DisposableEffect(player) {
        player.setOnCompletionListener {
            isPlaying = false
        }
        onDispose {
            runCatching {
                player.stop()
                player.release()
            }
        }
    }

    val playOrGenerate = {
        if (!isLoading) {
            scope.launch {
                try {
                    val currentExtra = VoiceMessageExtra.parse(msg.extra)
                    val existingPath = currentExtra.audioPath.takeIf { it.isNotBlank() && File(it).exists() }
                    val audioPath = if (existingPath != null) {
                        existingPath
                    } else {
                        if (isUser) {
                            statusText = "自己的语音暂不合成"
                            return@launch
                        }
                        val character = CharacterProfileRepository(context).getProfiles().firstOrNull { it.id == contactCharacterId }
                        if (character?.voiceId.isNullOrBlank()) {
                            statusText = "未绑定音色"
                            return@launch
                        }
                        val voiceProfile = VoiceServiceRepository(context).getActiveProfile()
                        if (voiceProfile == null) {
                            statusText = "未配置语音服务"
                            return@launch
                        }
                        isLoading = true
                        val result = VoiceServiceClient.synthesizeToFile(
                            context = context,
                            profile = voiceProfile,
                            voiceId = character!!.voiceId,
                            text = msg.content,
                            messageId = msg.id
                        )
                        onUpdateMessage(msg.copy(extra = VoiceMessageExtra(result.audioPath, result.durationMillis).toJson()))
                        result.audioPath
                    }

                    statusText = null
                    if (isPlaying) {
                        player.pause()
                        player.seekTo(0)
                        isPlaying = false
                    } else {
                        player.reset()
                        player.setDataSource(audioPath)
                        player.prepare()
                        player.start()
                        isPlaying = true
                    }
                } catch (e: Exception) {
                    statusText = e.message ?: "播放失败"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Card(
        modifier = modifier
            .width(188.dp)
            .clickable { playOrGenerate() },
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(
            topStart = if (isUser) 16.dp else 4.dp,
            topEnd = if (isUser) 4.dp else 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        )
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White.copy(alpha = if (isUser) 0.22f else 0.0f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                    )

                    isPlaying -> Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "正在播放",
                        tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )

                    else -> Icon(
                        imageVector = if (extra.audioPath.isBlank()) Icons.Default.RecordVoiceOver else Icons.Default.PlayArrow,
                        contentDescription = "播放语音",
                        tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = statusText ?: if (isPlaying) "播放中" else "语音",
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(24.dp))
        }
    }
}
