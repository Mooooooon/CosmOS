package com.moonlib.cosmos.ui.chat

import android.net.Uri
// import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.moonlib.cosmos.data.chat.ChatContact
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.ui.theme.LocalThemeConfig
import java.util.UUID

/**
 * 聊天 APP - 联系人编辑与创建页面
 *
 * 职责单一：提供新建/编辑联系人表单逻辑，支持图库头像上传、人设联动选择与必填项校验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    contactId: String?, // 如果为 null 则为新建联系人，如果不为 null 则为编辑现有联系人
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalThemeConfig.current.isDark
    val backgroundColor = if (isDark) Color.Black else Color.White

    val context = LocalContext.current
    val chatRepo = remember { ChatRepository(context) }
    val profileRepo = remember { CharacterProfileRepository(context) }

    // 1. 加载档案库中的角色列表
    val profiles = remember { profileRepo.getProfiles() }

    // 2. 区分是“新建”还是“编辑”，初始化表单状态
    val isEditMode = contactId != null
    val existingContact = remember(contactId) {
        if (contactId != null) {
            chatRepo.getContacts().firstOrNull { it.id == contactId }
        } else null
    }

    var nickname by remember { mutableStateOf(existingContact?.nickname ?: "") }
    var signature by remember { mutableStateOf(existingContact?.signature ?: "") }
    var avatarPath by remember { mutableStateOf(existingContact?.avatar ?: "") }
    var selectedProfileId by remember { mutableStateOf(existingContact?.characterId ?: "") }

    var expandedDropdown by remember { mutableStateOf(false) }
    val selectedProfileName = remember(selectedProfileId, profiles) {
        profiles.firstOrNull { it.id == selectedProfileId }?.name ?: "点击选择关联的系统人设"
    }

    // 确定唯一 ID
    val targetContactId = remember { existingContact?.id ?: UUID.randomUUID().toString() }

    // 图片选择器 Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 将外部 uri 拷贝到内部私有目录，返回安全持久的文件绝对路径
            val localPath = chatRepo.copyAvatarToLocal(it.toString(), targetContactId)
            if (localPath.isNotBlank()) {
                avatarPath = localPath
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑资料" else "新建联系人",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── 1. 圆形可上传头像框 ──────────────────────────────────
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.BottomEnd
            ) {
                AvatarView(
                    avatarPath = avatarPath,
                    name = nickname.ifBlank { "空" },
                    size = 100.dp
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "选择照片",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ─── 2. 表单输入字段 ─────────────────────────────────────
            
            // 昵称输入框
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("联系人昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 个性签名输入框
            OutlinedTextField(
                value = signature,
                onValueChange = { signature = it },
                label = { Text("个性签名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 联动选择系统人设档案
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "关联人设档案 (必选)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { expandedDropdown = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedProfileName,
                            color = if (selectedProfileId.isBlank()) 
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            else 
                                MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "选择下拉",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                // 自定义人设选择下拉菜单，避免 ExposedDropdownMenu 样式破裂
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (profiles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无档案，请前往【档案】APP创建角色", color = MaterialTheme.colorScheme.primary) },
                            onClick = { expandedDropdown = false }
                        )
                    } else {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    selectedProfileId = profile.id
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ─── 3. 保存/提交按钮 ─────────────────────────────────────
            Button(
                onClick = {
                    // 表单合理性验证
                    if (nickname.isBlank()) {
                        // Toast.makeText(context, "请输入联系人昵称！", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedProfileId.isBlank()) {
                        // Toast.makeText(context, "请关联一个人设档案！", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 创建或更新联系人并持久化
                    val contact = ChatContact(
                        id = targetContactId,
                        nickname = nickname.trim(),
                        avatar = avatarPath,
                        signature = signature.trim(),
                        characterId = selectedProfileId
                    )
                    chatRepo.saveContact(contact)
                    
                    val text = if (isEditMode) "保存成功！" else "联系人添加成功！"
                    // Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    onGoBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (isEditMode) "保存修改" else "点击添加",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
