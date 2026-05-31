package com.moonlib.cosmos.ui.settings

import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiSettingsRepository


/**
 * AI 通讯设置页面
 *
 * 职责单一：负责展示及修改 AI 通讯全局参数（如上下文消息数），提供极致扁平、对比度自适应的高级视觉操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiSettingsRepo = remember { AiSettingsRepository(context) }
    
    // 初始化上下文条数状态
    var maxContextSize by remember {
        mutableStateOf(aiSettingsRepo.getMaxContextSize())
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "通讯设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "系统上下文限额",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )

            // ── 1. 核心控制卡片 ────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "最大上下文消息数",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$maxContextSize 条",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // 滑动条 (范围为 10 到 500)
                    Slider(
                        value = maxContextSize.toFloat(),
                        onValueChange = { newValue ->
                            maxContextSize = newValue.roundToInt()
                        },
                        onValueChangeFinished = {
                            // 滑动松开时持久化
                            aiSettingsRepo.saveMaxContextSize(maxContextSize)
                        },
                        valueRange = 10f..500f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 辅助刻度
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "极简 (10)",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "默认 (100)",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "丰富 (500)",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Text(
                text = "时间跳过设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )


            // ── 2. 高级指南说明卡片 ────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💡 什么是上下文限额与跳过限制？",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "系统上下文限额决定了您在与 AI 角色进行聊天或互动时，系统每次向 AI 请求所附带的最近历史对话记忆的总长度。",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text(
                        text = "• 较小上下文限制 (10 ~ 20条)：AI 响应耗时低、极其节省 API 的 Token 流量消耗，但在长对话中 AI 可能会遗忘较早前发生的故事或谈论的主题。",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Text(
                        text = "• 系统默认上下文 (100条)：推荐配置。在绝大多数对话场景下，都能提供优秀的记忆回溯力与较快生成响应的平衡状态。",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Text(
                        text = "• 时间跳过单人限制 (1 ~ 100条)：决定了在虚拟时间 App 中跳过时间后，AI 最多会为每位有会话的角色模拟生成多少条离线消息。调低能极大节省 Token 流量消耗并提高时间跳过模拟速度；调高能让这期间收到的离线未读剧情更丰盈饱满。",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
