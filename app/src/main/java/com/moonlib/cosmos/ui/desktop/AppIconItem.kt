package com.moonlib.cosmos.ui.desktop

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.ui.theme.LightTextPrimary
import com.moonlib.cosmos.ui.theme.LocalThemeConfig
import com.moonlib.cosmos.ui.theme.StarWhite

/**
 * 单个 App 图标占位组件
 *
 * 显示渐变圆角背景 + 图标 + 标签文字，点击有缩放微动画。
 * 职责单一：只负责单枚图标的视觉呈现，不包含导航逻辑。
 */
@Composable
fun AppIconItem(
    app: DesktopApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    // 点击缩放动画
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            pressed -> 0.88f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh,
        ),
        label = "icon_scale",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标容器
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(app.color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector    = app.icon,
                contentDescription = app.label,
                tint           = StarWhite,
                modifier       = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // App 标签
        Text(
            text      = app.label,
            color     = if (isDark) StarWhite else LightTextPrimary,
            fontSize  = 11.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier.width(64.dp),
        )
    }

    // 释放动画还原
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}
