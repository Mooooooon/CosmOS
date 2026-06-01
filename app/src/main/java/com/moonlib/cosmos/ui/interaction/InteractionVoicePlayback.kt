package com.moonlib.cosmos.ui.interaction

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.moonlib.cosmos.data.interaction.InteractionVoiceTextExtractor
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.settings.SaveManager
import com.moonlib.cosmos.data.settings.VoiceServiceClient
import com.moonlib.cosmos.data.settings.VoiceServiceRepository
import java.io.File
import kotlinx.coroutines.launch

/**
 * 互动回复语音播放控制器。
 *
 * 职责单一：为角色互动回复执行按需合成、缓存与播放。
 */
@Stable
class InteractionVoicePlaybackState internal constructor(
    val isAvailable: Boolean,
    val isLoading: Boolean,
    val isPlaying: Boolean,
    val statusText: String?,
    val play: (String, String) -> Unit
)

@Composable
fun rememberInteractionVoicePlaybackState(
    characterId: String
): InteractionVoicePlaybackState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaPlayer = remember { MediaPlayer() }
    val character = remember(characterId) {
        CharacterProfileRepository(context).getProfiles().firstOrNull { it.id == characterId }
    }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
        }
        onDispose {
            runCatching {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    val play: (String, String) -> Unit = { messageId, content ->
        if (!isLoading) {
            scope.launch {
                val voiceId = character?.voiceId.orEmpty()
                if (voiceId.isBlank()) {
                    statusText = "未绑定音色"
                    return@launch
                }
                val speech = InteractionVoiceTextExtractor.extractSpeech(content)
                if (speech.isBlank()) {
                    statusText = "没有可播放的对白"
                    return@launch
                }
                val serviceProfile = VoiceServiceRepository(context).getActiveProfile()
                if (serviceProfile == null) {
                    statusText = "未配置语音服务"
                    return@launch
                }

                try {
                    val cacheId = "interaction_$messageId"
                    val cacheFile = File(context.filesDir, "${SaveManager.getAvatarDirName("voice_messages")}/$cacheId.mp3")
                    val audioPath = if (cacheFile.exists()) {
                        cacheFile.absolutePath
                    } else {
                        isLoading = true
                        VoiceServiceClient.synthesizeToFile(
                            context = context,
                            profile = serviceProfile,
                            voiceId = voiceId,
                            text = speech,
                            messageId = cacheId
                        ).audioPath
                    }

                    statusText = null
                    if (isPlaying) {
                        mediaPlayer.pause()
                        mediaPlayer.seekTo(0)
                        isPlaying = false
                    } else {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(audioPath)
                        mediaPlayer.prepare()
                        mediaPlayer.start()
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

    return InteractionVoicePlaybackState(
        isAvailable = character?.voiceId?.isNotBlank() == true,
        isLoading = isLoading,
        isPlaying = isPlaying,
        statusText = statusText,
        play = play
    )
}
