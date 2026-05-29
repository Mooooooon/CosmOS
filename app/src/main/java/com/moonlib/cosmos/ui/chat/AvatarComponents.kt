package com.moonlib.cosmos.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.io.File

// 精心设计的 8 种高饱和度、高对比度的宇宙风调色板（完美衬托白色文字）
private val AvatarColors = listOf(
    Color(0xFF2563EB), // 经典深蓝
    Color(0xFF7C3AED), // 皇家星云紫
    Color(0xFF0D9488), // 深海湖蓝
    Color(0xFFEA580C), // 熔岩烈橙
    Color(0xFF059669), // 极光翠绿
    Color(0xFF0891B2), // 赛博青蓝
    Color(0xFF9333EA), // 魅惑深紫
    Color(0xFFE11D48)  // 耀眼超新星红
)

/**
 * 获取基于名字 Hash 码的稳定颜色
 */
fun getAvatarColor(name: String): Color {
    if (name.isBlank()) return AvatarColors[0]
    val hash = name.hashCode()
    val index = Math.abs(hash) % AvatarColors.size
    return AvatarColors[index]
}

/**
 * 聊天 APP 丸型头像核心组件
 *
 * 职责单一：根据给定的本地头像路径或占位名渲染精致的圆形头像。
 * - 如果头像路径存在且图片文件有效：采用 Bitmap 原生解码加载，全自动圆角缩放裁剪。
 * - 如果路径为空、文件不存在或加载失败：提取名字的首个字符，以高对比度圆形彩色卡片衬托显示。
 */
@Composable
fun AvatarView(
    avatarPath: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarPath) {
        try {
            if (avatarPath.isNotBlank()) {
                val file = File(avatarPath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(avatarPath)?.asImageBitmap()
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    if (bitmap != null) {
        // 渲染本地图片头像
        Image(
            bitmap = bitmap,
            contentDescription = name,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        // 渲染首字动态背景占位头像
        val firstChar = remember(name) {
            name.trim().firstOrNull()?.toString() ?: "?"
        }
        val bgColor = remember(name) {
            getAvatarColor(name)
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstChar,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp
            )
        }
    }
}
