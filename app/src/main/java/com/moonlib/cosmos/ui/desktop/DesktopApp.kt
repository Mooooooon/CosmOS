package com.moonlib.cosmos.ui.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Memory
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
    /** 图标背景纯色 */
    val color: Color,
)

/** CosmOS 桌面预置 App 列表（占位，尚未实装功能） */
val desktopApps = listOf(
    DesktopApp(
        id    = "settings",
        label = "设置",
        icon  = Icons.Default.Settings,
        color = Color(0xFF374151),
    ),
    DesktopApp(
        id    = "profile",
        label = "档案",
        icon  = Icons.Default.Person,
        color = Color(0xFF1D4ED8),
    ),
    DesktopApp(
        id    = "chat",
        label = "聊天",
        icon  = Icons.Default.Forum,          // Forum 无弃用问题
        color = Color(0xFF059669),
    ),
    DesktopApp(
        id    = "interaction",
        label = "互动",
        icon  = Icons.Default.Favorite,
        color = Color(0xFFDC2626),
    ),
    DesktopApp(
        id    = "diary",
        label = "日记",
        icon  = Icons.Default.BookmarkBorder, // BookmarkBorder 替代 MenuBook
        color = Color(0xFFD97706),
    ),
    DesktopApp(
        id    = "memory",
        label = "记忆",
        icon  = Icons.Default.Memory,
        color = Color(0xFF4F46E5),
    ),
    DesktopApp(
        id    = "twitter",
        label = "推特",
        icon  = Icons.Default.Public,
        color = Color(0xFF0EA5E9),
    ),
    DesktopApp(
        id    = "time",
        label = "时间",
        icon  = Icons.Default.AccessTime,
        color = Color(0xFF7C3AED),
    ),
)
