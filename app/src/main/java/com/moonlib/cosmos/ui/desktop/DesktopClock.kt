package com.moonlib.cosmos.ui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 桌面时钟 Widget
 *
 * 展示虚拟世界的当前日期与时间。
 * 初期直接使用系统时间，后续替换为 VirtualTimeManager 提供的时间流。
 */
@Composable
fun DesktopClock(modifier: Modifier = Modifier) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    val currentTextColor = if (isDark) StarWhite else LightTextPrimary
    val currentSecColor = if (isDark) GlowCyan.copy(alpha = 0.7f) else NebulaPurple.copy(alpha = 0.8f)
    val currentSubColor = if (isDark) StarWhite.copy(alpha = 0.6f) else LightTextSecondary

    // 订阅全局虚拟时间
    val currentVirtualTime by VirtualTimeManager.currentTimeFlow.collectAsState()

    val now = remember(currentVirtualTime) {
        val instant = java.time.Instant.ofEpochMilli(currentVirtualTime)
        LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    }

    val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val secondStr = now.format(DateTimeFormatter.ofPattern("ss"))
    val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE"))

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 大时间数字
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeStr,
                color = currentTextColor,
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-2).sp,
                lineHeight = 72.sp,
            )
            Text(
                text = ":$secondStr",
                color = currentSecColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 日期
        Text(
            text = dateStr,
            color = currentSubColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 系统标识
        Text(
            text = "C O S M O S",
            color = if (isDark) NebulaPurple.copy(alpha = 0.8f) else NebulaPurple,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
        )
    }
}
