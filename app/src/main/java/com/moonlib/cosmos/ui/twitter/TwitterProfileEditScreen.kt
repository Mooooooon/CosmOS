package com.moonlib.cosmos.ui.twitter

import android.net.Uri
// import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.twitter.TwitterProfile
import com.moonlib.cosmos.data.twitter.TwitterRepository
import com.moonlib.cosmos.ui.chat.AvatarView

/**
 * 独立推特主页资料编辑页面
 * 
 * 职责单一：提供完整的推特头像更换、昵称自定义、唯一用户名（句柄）管理、个人简介修改，以及博主取关的逻辑和交互界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitterProfileEditScreen(
    profile: TwitterProfile,
    characterProfile: CharacterProfile?,
    repository: TwitterRepository,
    onBackClick: () -> Unit,
    onSaveClick: (TwitterProfile) -> Unit,
    onUnfollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUser = profile.characterId == "user"

    // ── 资料状态 ──────────────────────────────────────────────
    var nickname by remember { mutableStateOf(profile.nickname) }
    var username by remember { mutableStateOf(profile.username) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarPath by remember { mutableStateOf(profile.avatar) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showUnfollowDialog by remember { mutableStateOf(false) }

    // ── 图像选择器 ───────────────────────────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = repository.copyAvatarToLocal(it.toString(), profile.characterId)
            if (localPath.isNotBlank()) {
                avatarPath = localPath
                // Toast.makeText(context, "推特头像设置成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 是否存在变更 ─────────────────────────────────────────
    val hasChanges = remember(nickname, username, bio, avatarPath, profile) {
        nickname != profile.nickname ||
                username != profile.username ||
                bio != profile.bio ||
                avatarPath != profile.avatar
    }

    // 拦截物理返回键
    BackHandler {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    val isFormValid = nickname.isNotBlank() && username.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isUser) "编辑我的推特主页" else "自定义博主资料",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (hasChanges) {
                                showDiscardDialog = true
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    TwitterProfileAiGenerateAction(
                        characterProfile = characterProfile,
                        onGenerated = { generatedNickname, generatedUsername, generatedBio ->
                            nickname = generatedNickname
                            username = generatedUsername
                            bio = generatedBio
                        }
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 1. 头像自定义器 ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.BottomEnd
            ) {
                AvatarView(
                    avatarPath = avatarPath,
                    name = nickname.ifBlank { "推" },
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
                        contentDescription = "修改头像",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 2. 编辑表单 ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 昵称 TextField
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("昵称") },
                        placeholder = { Text("请输入推特上显示的社交昵称") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 用户名 handle TextField (@xxxx)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { input ->
                            // 限制用户名只能是字母数字下划线
                            username = input.filter { it.isLetterOrDigit() || it == '_' }
                        },
                        label = { Text("用户名 (@xxxx)") },
                        placeholder = { Text("由英文字母、数字或下划线组成") },
                        prefix = { Text("@") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 个人简介 Bio TextField
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("个人简介") },
                        placeholder = { Text("介绍一下自己或者写两句个性的简介吧...") },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 3. 保存与功能动作按钮 ─────────────────────────────────
            Button(
                onClick = {
                    if (isFormValid) {
                        val updated = profile.copy(
                            nickname = nickname.trim(),
                            username = username.trim().lowercase(),
                            bio = bio.trim(),
                            avatar = avatarPath
                        )
                        onSaveClick(updated)
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "保存资料",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFormValid) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // 非玩家自身，可以取关，极佳的高保真体验
            if (!isUser) {
                OutlinedButton(
                    onClick = { showUnfollowDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "取消关注该博主",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── 4. 退出拦截确认对话框 ──────────────────────────────
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃修改吗？", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("您编辑的推特资料尚未保存，如果离开，所有修改将彻底丢失。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑", color = MaterialTheme.colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── 5. 取关确认对话框 ──────────────────────────────────
    if (showUnfollowDialog) {
        AlertDialog(
            onDismissRequest = { showUnfollowDialog = false },
            title = { Text("确定取消关注吗？", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("取消关注后，您在公共时间线将无法阅读来自“${nickname}”发布的主动动态。并且其推特名片将退回到发现推荐中。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfollowDialog = false
                        onUnfollowClick()
                    }
                ) {
                    Text("取消关注", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowDialog = false }) {
                    Text("继续关注", color = MaterialTheme.colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
