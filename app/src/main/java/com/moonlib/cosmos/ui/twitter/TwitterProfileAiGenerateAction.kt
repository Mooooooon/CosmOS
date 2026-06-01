package com.moonlib.cosmos.ui.twitter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.twitter.TwitterProfileMetadataGenerator
import kotlinx.coroutines.launch

/**
 * 推特资料编辑页右上角 AI 生成动作。
 *
 * 职责单一：根据关联人设生成公开社交平台昵称、用户名与简介，并回填表单。
 */
@Composable
fun TwitterProfileAiGenerateAction(
    characterProfile: CharacterProfile?,
    onGenerated: (nickname: String, username: String, bio: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isProfileValid = characterProfile != null

    IconButton(
        onClick = {
            val profile = characterProfile ?: return@IconButton
            isGenerating = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val result = TwitterProfileMetadataGenerator.generate(
                        context = context,
                        characterProfile = profile
                    )
                    onGenerated(result.nickname, result.username, result.bio)
                } catch (e: Exception) {
                    errorMessage = e.message ?: "AI 生成失败，请检查模型配置后重试。"
                } finally {
                    isGenerating = false
                }
            }
        },
        enabled = isProfileValid && !isGenerating,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isProfileValid) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI生成资料",
                    tint = if (isProfileValid) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = {
                Text(
                    text = "生成失败",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("我知道了")
                }
            }
        )
    }
}
