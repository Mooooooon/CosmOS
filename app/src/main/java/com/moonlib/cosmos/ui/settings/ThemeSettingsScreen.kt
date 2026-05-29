package com.moonlib.cosmos.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.ui.theme.*

/**
 * 主题与个性化设置页面
 *
 * 职责单一：负责渲染主题模式选择界面，支持预览深色/浅色卡片，点击即可瞬间应用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "个性化",
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
                text = "选择系统外观样式",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )

            // ── 1. 深色主题卡片 ────────────────────────────────────
            ThemeOptionCard(
                title = "深空星云 (深色)",
                description = "经典的暗黑宇宙风格，低光环境下倍感舒适，科技感十足。",
                isSelected = isDark,
                onClick = { themeConfig.setDarkTheme(true) },
                previewContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(SpaceDeepBlack, SpaceNavy, SpaceIndigo)
                                )
                            )
                    ) {
                        // 绘制微缩的白色状态栏与大时钟
                        MiniSystemPreview(isDarkPreview = true)
                    }
                }
            )

            // ── 2. 浅色主题卡片 ────────────────────────────────────
            ThemeOptionCard(
                title = "晨曦极光 (浅色)",
                description = "明亮素雅的淡雅风格，高对比度，阳光下字迹清晰，高级典雅。",
                isSelected = !isDark,
                onClick = { themeConfig.setDarkTheme(false) },
                previewContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFEBEFFE), Color(0xFFE0E7FF), Color(0xFFF3F4F6))
                                )
                            )
                    ) {
                        // 绘制微缩的黑色状态栏与大时钟
                        MiniSystemPreview(isDarkPreview = false)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 主题可选项交互卡片
 */
@Composable
private fun ThemeOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    previewContent: @Composable BoxScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        label = "border_color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 1.dp,
        label = "border_width"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .shadow(
                elevation = if (isSelected) 4.dp else 1.dp,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部可视化预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                previewContent()
            }

            // 底部文字介绍与选中标识区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )
                }
            }
        }
    }
}

/**
 * 极简微缩的系统页面预览（用于主题卡片内嵌）
 */
@Composable
private fun MiniSystemPreview(isDarkPreview: Boolean) {
    val textColor = if (isDarkPreview) StarWhite else LightTextPrimary
    val statusColor = if (isDarkPreview) StatusIconColor else LightStatusIconColor

    Column(modifier = Modifier.fillMaxSize()) {
        // 微型状态栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "12:00",
                color = textColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 迷你信号/电量占位
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(statusColor)
                )
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(statusColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 微型时钟
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "12:00",
                color = textColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Thin,
                lineHeight = 32.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "5月29日 星期五",
                color = textColor.copy(alpha = 0.6f),
                fontSize = 8.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 迷你应用图标占位
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (index) {
                                0 -> Color(0xFF8B5CF6)
                                1 -> Color(0xFF10B981)
                                2 -> Color(0xFF3B82F6)
                                else -> Color(0xFFF59E0B)
                            }
                        )
                )
            }
        }
    }
}
