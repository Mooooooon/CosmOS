package com.moonlib.cosmos.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiConfigRepository
import com.moonlib.cosmos.data.settings.AiProfile
import com.moonlib.cosmos.data.profile.CharacterNameGenerator
import kotlinx.coroutines.launch

/**
 * AI 取名备选与填入对话框
 * 
 * 职责单一：负责与 AI 通讯获取 10 个名字推荐，展示列表供用户选择，支持填入和重新生成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNameRecommendationDialog(
    prompt: String,
    onDismiss: () -> Unit,
    onNameSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configRepo = remember { AiConfigRepository(context) }

    // ── 状态声明 ──────────────────────────────────────────────
    var activeProfile by remember { mutableStateOf<AiProfile?>(null) }
    var nameList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingConfig by remember { mutableStateOf(true) }

    // 获取名字的挂起函数
    fun fetchNames() {
        isLoading = true
        errorMessage = null
        selectedName = null
        coroutineScope.launch {
            try {
                val results = CharacterNameGenerator.generateNames(context, prompt)
                nameList = results
            } catch (e: Exception) {
                errorMessage = e.message ?: "通信超时，请检查您的 API Key、端点及网络。"
            } finally {
                isLoading = false
            }
        }
    }

    // 1. 初始化检查激活的 AI 配置文件并获取首批名字
    LaunchedEffect(Unit) {
        val profile = configRepo.getActiveProfile()
        activeProfile = profile
        isCheckingConfig = false
        if (profile != null) {
            fetchNames()
        }
    }

    if (isCheckingConfig) {
        return
    }

    val profile = activeProfile
    if (profile == null) {
        // 未激活 AI 服务提示
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
                    text = "系统当前未检测到任何已激活的 AI 模型服务。\n\n请先返回桌面进入【设置 -> AI模型服务配置】中添加并点击激活至少一个模型服务商（OpenAI、DeepSeek、Gemini、MiniMax），然后再试。",
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
        AlertDialog(
            onDismissRequest = { if (!isLoading) onDismiss() },
            title = {
                Column {
                    Text(
                        text = "AI 智绘灵感取名",
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.5.dp
                                )
                                Text(
                                    text = "正在根据人设构思名字...",
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
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "获取备选名字失败：",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "请点击选中以下为您量身定制的 10 个备选姓名：",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        // 网格排版名字，每行 2 个，共 5 行
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chunkedNames = nameList.chunked(2)
                            chunkedNames.forEach { rowNames ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowNames.forEach { name ->
                                        val isSelected = selectedName == name
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clickable { selectedName = name },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                }
                                            ),
                                            border = if (isSelected) {
                                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                            } else {
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = name,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                    if (rowNames.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLoading) {
                        OutlinedButton(
                            onClick = { fetchNames() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "重新生成",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isSelectValid = selectedName != null
                        Button(
                            onClick = {
                                selectedName?.let {
                                    onNameSelected(it)
                                    onDismiss()
                                }
                            },
                            enabled = isSelectValid,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "填入姓名",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelectValid) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
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
