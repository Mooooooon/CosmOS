package com.moonlib.cosmos.ui.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.moonlib.cosmos.ui.theme.*

/**
 * 桌面 App 元数据
 *
 * 描述一个 App 图标在桌面上显示所需的全部信息。
 * 每个 [DesktopApp] 职责单一：仅作数据容器。
 */
data class DesktopApp(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** 图标背景渐变起始色 */
    val colorStart: Color,
    /** 图标背景渐变结束色 */
    val colorEnd: Color,
)

/** CosmOS 桌面预置 App 列表（占位，尚未实装功能） */
val desktopApps = listOf(
    DesktopApp(
        id         = "settings",
        label      = "设置",
        icon       = Icons.Default.Settings,
        colorStart = Color(0xFF374151),
        colorEnd   = Color(0xFF1F2937),
    ),
    DesktopApp(
        id         = "profile",
        label      = "档案",
        icon       = Icons.Default.Person,
        colorStart = Color(0xFF1D4ED8),
        colorEnd   = Color(0xFF1E3A8A),
    ),
    DesktopApp(
        id         = "chat",
        label      = "聊天",
        icon       = Icons.Default.Forum,          // Forum 无弃用问题
        colorStart = Color(0xFF059669),
        colorEnd   = Color(0xFF065F46),
    ),
    DesktopApp(
        id         = "interaction",
        label      = "互动",
        icon       = Icons.Default.Favorite,
        colorStart = Color(0xFFDC2626),
        colorEnd   = Color(0xFF991B1B),
    ),
    DesktopApp(
        id         = "diary",
        label      = "日记",
        icon       = Icons.Default.BookmarkBorder, // BookmarkBorder 替代 MenuBook
        colorStart = Color(0xFFD97706),
        colorEnd   = Color(0xFF92400E),
    ),
    DesktopApp(
        id         = "twitter",
        label      = "推特",
        icon       = Icons.Default.Public,
        colorStart = Color(0xFF0EA5E9),
        colorEnd   = Color(0xFF075985),
    ),
    DesktopApp(
        id         = "time",
        label      = "时间",
        icon       = Icons.Default.AccessTime,
        colorStart = Color(0xFF7C3AED),
        colorEnd   = Color(0xFF4C1D95),
    ),
)
