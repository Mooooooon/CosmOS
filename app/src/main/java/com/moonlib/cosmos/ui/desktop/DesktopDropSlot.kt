package com.moonlib.cosmos.ui.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.ui.theme.LocalThemeConfig

/**
 * 长按移动图标时显示的可落位槽。
 *
 * 职责单一：呈现桌面图标允许放置的范围。
 */
@Composable
fun DesktopDropSlot(
    visible: Boolean,
    isTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalThemeConfig.current.isDark
    val outlineColor = when {
        !visible -> Color.Transparent
        isTarget -> if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.55f)
        else -> if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f)
    }
    val backgroundColor = when {
        !visible -> Color.Transparent
        isTarget -> if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.42f)
        else -> Color.Transparent
    }
    val animatedOutline = animateColorAsState(outlineColor, label = "drop_slot_outline")
    val animatedBackground = animateColorAsState(backgroundColor, label = "drop_slot_background")
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBackground.value, shape)
            .border(1.dp, animatedOutline.value, shape),
    )
}
