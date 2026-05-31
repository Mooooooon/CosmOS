package com.moonlib.cosmos.ui.chat

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.data.chat.ContactMetadataGenerator
import com.moonlib.cosmos.data.profile.CharacterProfile
import kotlinx.coroutines.launch

/**
 * 联系人编辑页右上角 AI 生成动作。
 *
 * 职责单一：根据当前关联人设触发昵称 / 个性签名生成，并把结果回填给表单。
 */
@Composable
fun ContactAiGenerateAction(
    selectedProfile: CharacterProfile?,
    onGenerated: (nickname: String, signature: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (isGenerating) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        TextButton(
            enabled = selectedProfile != null,
            onClick = {
                val profile = selectedProfile ?: return@TextButton
                isGenerating = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        val result = ContactMetadataGenerator.generate(context, profile)
                        onGenerated(result.nickname, result.signature)
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "AI 生成失败，请检查模型配置后重试。"
                    } finally {
                        isGenerating = false
                    }
                }
            }
        ) {
            Text(
                text = "AI生成",
                fontWeight = FontWeight.Bold
            )
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
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("我知道了")
                }
            }
        )
    }
}
