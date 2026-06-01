package com.moonlib.cosmos.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.ui.theme.*
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

@Composable
fun VirtualStatusBar(modifier: Modifier = Modifier) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    val currentBg = Color.Transparent
    val currentIconColor = if (isDark) StatusIconColor else LightStatusIconColor
    val currentTextColor = if (isDark) StarWhite else LightTextPrimary

    // 订阅系统时间
    val currentVirtualTime by VirtualTimeManager.currentTimeFlow.collectAsState()

    val timeText = remember(currentVirtualTime) {
        val instant = java.time.Instant.ofEpochMilli(currentVirtualTime)
        val ldt = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)                 // 调高为更舒展、大气的 44dp 高度
            .background(currentBg),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── 左侧：系统时间 ──────────────────────────────
            Text(
                text          = timeText,
                color         = currentTextColor,
                fontSize      = 15.sp,         // 时间字体调整至 15sp，与右侧电量保持一致
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                style         = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )

            // ── 右侧：系统状态图标 ────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp), // 间距优化为更高级的 10dp
            ) {
                Icon(
                    imageVector        = Icons.Default.NetworkCell,
                    contentDescription = "信号",
                    tint               = currentIconColor,
                    modifier           = Modifier.size(18.dp), // 图标放大至 18dp
                )
                Icon(
                    imageVector        = Icons.Default.Wifi,
                    contentDescription = "WiFi",
                    tint               = currentIconColor,
                    modifier           = Modifier.size(18.dp), // 图标放大至 18dp
                )
                BatteryIndicator(tint = currentIconColor)
            }
        }
    }
}

/** 简易电量数字（纯文字，避免依赖外部图标资源） */
@Composable
private fun BatteryIndicator(tint: Color) {
    Text(
        text          = "100",
        color         = tint,
        fontSize      = 15.sp,                 // 电量字体放大至 15sp
        fontWeight    = FontWeight.SemiBold,   // 采用与时间一致的 SemiBold 字重，确保视觉对齐与厚度和谐
        letterSpacing = 0.5.sp,
        style         = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )
}
