package com.moonlib.cosmos.ui.interaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.ui.chat.AvatarView
import com.moonlib.cosmos.ui.theme.LocalThemeConfig

/**
 * 互动 APP 角色列表界面
 *
 * 职责单一：渲染来自“档案”的非用户角色列表，采用只展示姓名与首字头像的极简设计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionListScreen(
    onNavigateTo: (InteractionNavigation) -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    // 从档案仓库中拉取角色列表
    val profileRepo = remember { CharacterProfileRepository(context) }
    val characterProfiles = remember {
        profileRepo.getProfiles().filter { !it.isPlayer }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "互动",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExitApp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回桌面",
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
        if (characterProfiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "暂无互动角色",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "请先前往【档案】应用中新建角色设定",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
            ) {
                items(characterProfiles, key = { it.id }) { character ->
                    // 首字圆形头像
                    val firstChar = remember(character.name) {
                        if (character.name.isNotBlank()) character.name.take(1) else "?"
                    }
                    val avatarBgColor = remember(character.name, isDark) {
                        getAvatarBgColor(character.name, isDark)
                    }
                    val avatarTextColor = remember(isDark) {
                        if (isDark) Color(0xFFECEFF4) else Color(0xFF2E3440)
                    }

                    // 极致质感卡片
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigateTo(InteractionNavigation.Conversation(character.id)) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 圆形头像
                            AvatarView(
                                avatarPath = character.avatar,
                                name = character.name,
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // 角色姓名
                            Text(
                                text = character.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            // 进入按钮箭头
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "进入互动",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 莫兰迪色系的自适应背景生成函数，专为高贵感微粒设计
 */
private fun getAvatarBgColor(name: String, isDark: Boolean): Color {
    val colors = if (isDark) {
        listOf(
            Color(0xFF2E3846), Color(0xFF233B32), Color(0xFF382B3E),
            Color(0xFF3C2F2F), Color(0xFF1E3A47), Color(0xFF2C3E50)
        )
    } else {
        listOf(
            Color(0xFFE8ECEF), Color(0xFFE2F0D9), Color(0xFFFBE4D8),
            Color(0xFFF2E5F9), Color(0xFFE6F4F8), Color(0xFFFBF4D7)
        )
    }
    val hash = name.hashCode()
    val index = Math.abs(hash) % colors.size
    return colors[index]
}
