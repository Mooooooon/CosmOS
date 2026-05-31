package com.moonlib.cosmos.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moonlib.cosmos.data.chat.ChatRepository

/**
 * 聊天 APP 主界面（消息、联系人 Tab 页）
 *
 * 职责单一：渲染顶部个人栏与底部 Tab Row，管理当前活动 Tab 状态并承载个人配置编辑 Dialog。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMainScreen(
    activeTab: Int,
    onActiveTabChange: (Int) -> Unit,
    onNavigateTo: (ChatNavigation) -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatRepo = remember { ChatRepository(context) }

    // 观察用户自身的资料状态
    var userNickname by remember { mutableStateOf(chatRepo.getUserNickname()) }
    var userAvatar by remember { mutableStateOf(chatRepo.getUserAvatar()) }

    var showEditProfileDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            if (activeTab != 2) {
                // ─── 1. 精美顶部栏（高仿 QQ 风格，去掉了返回按钮） ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 用户头像与昵称区（点击修改自己资料）
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEditProfileDialog = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            avatarPath = userAvatar,
                            name = userNickname,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = userNickname,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Text(
                                text = "在线",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // 右侧加号按钮（点击添加联系人，使用主色）
                    IconButton(
                        onClick = { onNavigateTo(ChatNavigation.EditContact(null)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加联系人",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.statusBarsPadding())
            }

            // ─── 2. 页签内容渲染 ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTab == 0) {
                    MessageListTab(
                        onNavigateTo = onNavigateTo,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (activeTab == 1) {
                    ContactListTab(
                        onNavigateTo = onNavigateTo,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ChatMomentTab(
                        onNavigateTo = onNavigateTo,
                        onGoBack = { onActiveTabChange(0) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ─── 3. 底部 Tab 导航栏 (小字 + Icon) ─────────────────────────
            if (activeTab != 2) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(64.dp)
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { onActiveTabChange(0) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "消息"
                            )
                        },
                        label = {
                            Text(
                                text = "消息",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { onActiveTabChange(1) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "联系人"
                            )
                        },
                        label = {
                            Text(
                                text = "联系人",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { onActiveTabChange(2) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "动态"
                            )
                        },
                        label = {
                            Text(
                                text = "动态",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    }
    }

    // ─── 4. 修改个人资料 Dialog ─────────────────────────────────────
    if (showEditProfileDialog) {
        var tempNickname by remember { mutableStateOf(userNickname) }
        var tempAvatarPath by remember { mutableStateOf(userAvatar) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                val path = chatRepo.copyAvatarToLocal(it.toString(), "user")
                if (path.isNotBlank()) {
                    tempAvatarPath = path
                }
            }
        }

        Dialog(onDismissRequest = { showEditProfileDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "编辑个人资料",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // 头像框上传交互
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        AvatarView(
                            avatarPath = tempAvatarPath,
                            name = tempNickname.ifBlank { "我" },
                            size = 90.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "上传头像",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 昵称输入框
                    OutlinedTextField(
                        value = tempNickname,
                        onValueChange = { tempNickname = it },
                        label = { Text("我的昵称") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditProfileDialog = false }) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (tempNickname.isNotBlank()) {
                                    chatRepo.saveUserProfile(tempNickname, tempAvatarPath)
                                    userNickname = tempNickname
                                    userAvatar = tempAvatarPath
                                    showEditProfileDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("保存", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
