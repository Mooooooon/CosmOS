package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.ui.theme.LocalThemeConfig
import com.moonlib.cosmos.ui.theme.StarWhite

/**
 * 设置 APP 主屏幕
 *
 * 职责单一：渲染设置项目的分类列表，不涉及子页面的路由跳转逻辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    activeProfileName: String,
    activeVoiceServiceName: String,
    activeSaveName: String,
    onModelServiceClick: () -> Unit,
    onVoiceServiceClick: () -> Unit,
    onPromptClick: () -> Unit,
    onThemeClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAiChatSettingsClick: () -> Unit,
    onSaveManagerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. 顶部用户账户卡片 ────────────────────────────────────
            UserAccountCard()

            // ── 2. 网络和连接 ─────────────────────────────────────────
            SettingGroup(title = "连接与通信") {
                SimulatedSettingItem(
                    icon = Icons.Default.Wifi,
                    iconBgColor = Color(0xFF3B82F6),
                    title = "无线网络 (Wi-Fi)",
                    subtitle = "已连接 to CosmOS_SpaceNet"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SimulatedSettingItem(
                    icon = Icons.Default.NetworkCell,
                    iconBgColor = Color(0xFF10B981),
                    title = "移动网络",
                    subtitle = "星际 6G · 信号良好"
                )
            }

            // ── 3. 核心功能：模型服务 ──────────────────────────────────
            SettingGroup(title = "核心配置") {
                SettingItem(
                    icon = Icons.Default.Psychology,
                    iconBgColor = Color(0xFF8B5CF6),
                    title = "模型服务",
                    subtitle = activeProfileName,
                    onClick = onModelServiceClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.RecordVoiceOver,
                    iconBgColor = Color(0xFFE11D48),
                    title = "语音服务",
                    subtitle = activeVoiceServiceName,
                    onClick = onVoiceServiceClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.Description,
                    iconBgColor = Color(0xFF3B82F6),
                    title = "提示词设置",
                    subtitle = "配置全局主提示词与指令要求",
                    onClick = onPromptClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.Tune,
                    iconBgColor = Color(0xFFF59E0B),
                    title = "通讯设置",
                    subtitle = "设置AI聊天与互动的上下文消息数限制",
                    onClick = onAiChatSettingsClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.Terminal,
                    iconBgColor = Color(0xFF10B981),
                    title = "日志查询",
                    subtitle = "查看AI通讯与提示词记录",
                    onClick = onLogsClick
                )
            }

            // ── 4. 模拟系统设置项 ──────────────────────────────────────
            val isDark = LocalThemeConfig.current.isDark
            val themeSubtitle = if (isDark) "当前主题：深空星云 (深色)" else "当前主题：晨曦极光 (浅色)"

            SettingGroup(title = "系统与维护") {
                SettingItem(
                    icon = Icons.Default.Palette,
                    iconBgColor = Color(0xFFEC4899),
                    title = "个性化",
                    subtitle = themeSubtitle,
                    onClick = onThemeClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.Backup,
                    iconBgColor = Color(0xFF3B82F6),
                    title = "存档管理",
                    subtitle = "当前装载：$activeSaveName",
                    onClick = onSaveManagerClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                SimulatedSettingItem(
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFFF59E0B),
                    title = "关于虚拟手机",
                    subtitle = "CosmOS v1.0.0 (Android 16 兼容)"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 个人账户卡片 UI
 */
@Composable
private fun UserAccountCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "头像",
                    tint = StarWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "CosmOS 联络员",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "系统已连接到星际神经网络",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * 分组容器组件
 */
@Composable
private fun SettingGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * 标准设置点击项
 */
@Composable
private fun SettingItem(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = StarWhite,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 模拟的设置项目（静态展示）
 */
@Composable
private fun SimulatedSettingItem(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = StarWhite,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}
