package com.moonlib.cosmos.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiProfile
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileGenerator
import kotlinx.coroutines.launch

/**
 * AI 想法输入与智绘人设对话框
 * 
 * 职责单一：收集用户的想法创意，显示 AI 请求加载进度，并在成功后返回生成的人设文本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiIdeaInputDialog(
    availableProfiles: List<CharacterProfile> = emptyList(),
    onDismiss: () -> Unit,
    onGenerateSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configRepo = remember { AiConfigRepository(context) }

    // ── 状态声明 ──────────────────────────────────────────────
    var activeProfile by remember { mutableStateOf<AiProfile?>(null) }
    var ideaText by remember { mutableStateOf("") }
    var selectedReferenceIds by remember { mutableStateOf(emptySet<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingConfig by remember { mutableStateOf(true) }

    // 1. 初始化检查激活的 AI 配置文件
    LaunchedEffect(Unit) {
        activeProfile = configRepo.getActiveProfile()
        isCheckingConfig = false
    }

    if (isCheckingConfig) {
        // 静默状态
        return
    }

    val profile = activeProfile
    if (profile == null) {
        // ── 状态一：系统未激活任何 AI 配置 ───────────────────────────
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "未激活 AI 服务",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "系统当前未检测到任何激活的 AI 模型服务。\n\n请先返回桌面进入【设置 -> AI模型服务配置】中添加并点击激活至少一个模型服务商（OpenAI、DeepSeek、Gemini），然后再试。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "我知道了",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    } else {
        // ── 状态二：灵感录入与生成中 ─────────────────────────────────
        val isFormValid = ideaText.isNotBlank()
        val playerProfile = availableProfiles.firstOrNull { it.isPlayer }
        val referenceProfiles = availableProfiles.filter { !it.isPlayer && it.prompt.isNotBlank() }

        AlertDialog(
            onDismissRequest = { if (!isLoading) onDismiss() },
            title = {
                Column {
                    Text(
                        text = "AI 智能描绘人设",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "使用当前激活的模型: ${profile.modelName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLoading) {
                        // 菊花加载进度指示器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.5.dp
                                )
                                Text(
                                    text = "正在为您智能构思人设...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "根据接口性能，大约需要 5~15 秒，请稍后",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        // 灵感输入框
                        OutlinedTextField(
                            value = ideaText,
                            onValueChange = { ideaText = it },
                            placeholder = {
                                Text(
                                    text = "请输入您脑海里关于这个人设的想法（例如：一个隐居山林的世外剑客，身世神秘，性格温和但剑术极高，关键时刻非常靠谱...）",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            },
                            minLines = 4,
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        AiReferenceProfileSelector(
                            profiles = referenceProfiles,
                            selectedProfileIds = selectedReferenceIds,
                            onSelectionChange = { selectedReferenceIds = it }
                        )

                        // 错误状态栏
                        errorMessage?.let { err ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "智能智绘失败：",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = err,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isLoading) {
                    Button(
                        onClick = {
                            if (isFormValid) {
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    try {
                                        val selectedReferences = referenceProfiles.filter { it.id in selectedReferenceIds }
                                        val result = CharacterProfileGenerator.generateProfile(
                                            context = context,
                                            userIdea = ideaText,
                                            playerProfile = playerProfile,
                                            referenceProfiles = selectedReferences
                                        )
                                        onGenerateSuccess(result)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "通信超时，请检查您的 API Key、端点及网络。"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = isFormValid,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("开始智绘")
                    }
                }
            },
            dismissButton = {
                if (!isLoading) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "取消",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
