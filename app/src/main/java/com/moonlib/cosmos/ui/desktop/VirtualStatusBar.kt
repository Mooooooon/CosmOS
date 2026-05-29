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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun VirtualStatusBar(modifier: Modifier = Modifier) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    val currentBg = if (isDark) StatusBarBg else LightStatusBarBg
    val currentIconColor = if (isDark) StatusIconColor else LightStatusIconColor
    val currentTextColor = if (isDark) StarWhite else LightTextPrimary

    // 每分钟刷新一次虚拟时间（初期直接使用系统时间，后续接 TimeManager）
    var timeText by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            val secondsUntilNextMinute = 60 - now.second
            delay(secondsUntilNextMinute * 1000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)                 // 调高为更舒展、大气的 38dp 高度
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
            // ── 左侧：虚拟时间 ──────────────────────────────
            Text(
                text          = timeText,
                color         = currentTextColor,
                fontSize      = 14.sp,         // 时间字体放大至 14sp
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )

            // ── 右侧：装饰性系统图标 ────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp), // 间距优化为更高级的 8dp
            ) {
                Icon(
                    imageVector        = Icons.Default.NetworkCell,
                    contentDescription = "信号",
                    tint               = currentIconColor,
                    modifier           = Modifier.size(15.dp), // 图标放大至 15dp
                )
                Icon(
                    imageVector        = Icons.Default.Wifi,
                    contentDescription = "WiFi",
                    tint               = currentIconColor,
                    modifier           = Modifier.size(15.dp), // 图标放大至 15dp
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
        fontSize      = 13.sp,                 // 电量字体放大至 13sp
        fontWeight    = FontWeight.Medium,
        letterSpacing = 0.sp,
    )
}
