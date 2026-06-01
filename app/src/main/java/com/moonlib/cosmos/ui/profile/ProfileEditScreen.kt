package com.moonlib.cosmos.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.platform.LocalContext
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.ui.chat.AvatarView
import com.moonlib.cosmos.ui.theme.LocalThemeConfig
import java.util.UUID

/**
 * 档案新建与编辑页面
 * 
 * 职责单一：负责输入姓名和人设提示词，提供安全返回对话框拦截与删除确认对话框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    initialProfile: CharacterProfile?,
    profiles: List<CharacterProfile> = emptyList(),
    isPlayer: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (CharacterProfile) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark
    val isEditMode = initialProfile != null

    val context = LocalContext.current
    val repository = remember { CharacterProfileRepository(context) }
    val targetProfileId = remember { initialProfile?.id ?: UUID.randomUUID().toString() }

    // ── 核心输入状态 ──────────────────────────────────────────────
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var prompt by remember { mutableStateOf(initialProfile?.prompt ?: "") }
    var avatarPath by remember { mutableStateOf(initialProfile?.avatar ?: "") }
    var voiceId by remember { mutableStateOf(initialProfile?.voiceId ?: "") }
    var voiceName by remember { mutableStateOf(initialProfile?.voiceName ?: "") }
    // 关键词列表以逗号分隔的字符串形式展示，保存时拆分为列表
    var keywordsText by remember { mutableStateOf(initialProfile?.keywords?.joinToString(", ") ?: "") }

    // ── 对话框显示状态 ────────────────────────────────────────
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showNameAiDialog by remember { mutableStateOf(false) }

    // ── 图片选择器 Launcher ────────────────────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = repository.copyAvatarToLocal(it.toString(), targetProfileId)
            if (localPath.isNotBlank()) {
                avatarPath = localPath
            }
        }
    }

    // ── 检查是否有未保存的改动 ────────────────────────────────
    val hasChanges = remember(name, prompt, avatarPath, voiceId, voiceName, keywordsText, initialProfile) {
        val originalName = initialProfile?.name ?: ""
        val originalPrompt = initialProfile?.prompt ?: ""
        val originalAvatar = initialProfile?.avatar ?: ""
        val originalVoiceId = initialProfile?.voiceId ?: ""
        val originalVoiceName = initialProfile?.voiceName ?: ""
        val originalKeywords = initialProfile?.keywords?.joinToString(", ") ?: ""
        name != originalName ||
            prompt != originalPrompt ||
            avatarPath != originalAvatar ||
            voiceId != originalVoiceId ||
            voiceName != originalVoiceName ||
            keywordsText != originalKeywords
    }

    // 物理返回键安全拦截
    BackHandler(enabled = true) {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    val isFormValid = name.isNotBlank() && prompt.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isPlayer) {
                            if (isEditMode) "编辑用户人设" else "配置用户人设"
                        } else {
                            if (isEditMode) "编辑角色人设" else "新建角色人设"
                        },
                        fontSize = 19.sp,
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
                    // 若是编辑模式，在 TopBar 放置垃圾桶图标，清爽专业
                    if (isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "删除档案",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── 2. 文本输入表单 ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 头像上传框
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            AvatarView(
                                avatarPath = avatarPath,
                                name = name.ifBlank { if (isPlayer) "我" else "角" },
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
                                    contentDescription = "选择照片",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 姓名输入
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("姓名") },
                        placeholder = { Text("请输入或由 AI 智能取名") },
                        singleLine = true,
                        trailingIcon = {
                            val isPromptValid = prompt.isNotBlank()
                            IconButton(
                                onClick = { showNameAiDialog = true },
                                enabled = isPromptValid,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            color = if (isPromptValid) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI 取名",
                                        tint = if (isPromptValid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 人设提示词多行输入
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "人设提示词",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                placeholder = { Text("请详细输入该人设的性格特征、身世背景、口吻喜好等核心提示词，或点击右上角 AI 星标一键智能构思生成。") },
                                minLines = 7,
                                maxLines = 15,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 极简扁平圆形 AI 智绘按钮，绝对定位在右上角
                            IconButton(
                                onClick = { showAiDialog = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI 智绘人设",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        
                        // 底部右侧字数统计
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, end = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "共 ${prompt.length} 字",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // ── 关键词输入（仅非玩家角色显示） ──────────────────
                    if (!isPlayer) {
                        ProfileVoiceBindingSection(
                            voiceId = voiceId,
                            voiceName = voiceName,
                            onVoiceSelected = { option ->
                                voiceId = option?.id.orEmpty()
                                voiceName = option?.name.orEmpty()
                            }
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "关键词",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = keywordsText,
                                onValueChange = { keywordsText = it },
                                label = { Text("称呼 / 外号（逗号分隔）") },
                                placeholder = { Text("如：小美, meimei, 美美姐，多个用逗号分隔") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "当用户在互动/日记中提到这些关键词时，该角色人设会被自动提供给 AI。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── 3. 保存动作按键 ─────────────────────────────────────
            Button(
                onClick = {
                    if (isFormValid) {
                        val keywords = keywordsText
                            .split(",", "，")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        val finalProfile = CharacterProfile(
                            id = targetProfileId,
                            name = name.trim(),
                            prompt = prompt.trim(),
                            isPlayer = isPlayer,
                            avatar = avatarPath,
                            voiceId = voiceId,
                            voiceName = voiceName,
                            keywords = keywords
                        )
                        onSaveClick(finalProfile)
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "保存定案",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFormValid) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── 4. 未保存强退提示 Dialog ──────────────────────────────
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = "放弃修改？",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "检测到您当前有人设内容已修改，如果现在退出，所有未保存的改动将会永久丢失。",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text(
                        text = "放弃修改",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(
                        text = "继续编辑",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    // ── 5. 删除确认 Dialog ──────────────────────────────────
    if (showDeleteDialog && isEditMode) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "确认删除？",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "您确定要彻底删除角色人设“${initialProfile?.name}”吗？此操作不可撤销，删除后人设提示词将彻底被抹去。",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        initialProfile?.id?.let { onDeleteClick(it) }
                    }
                ) {
                    Text(
                        text = "彻底删除",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    // ── 6. AI 想法输入 Dialog ────────────────────────────────
    if (showAiDialog) {
        val aiReferenceProfiles = profiles.filter { it.id != targetProfileId }
        AiIdeaInputDialog(
            availableProfiles = if (isPlayer) emptyList() else aiReferenceProfiles,
            onDismiss = { showAiDialog = false },
            onGenerateSuccess = { prompt = it }
        )
    }

    // ── 7. AI 取名 Dialog ───────────────────────────────────
    if (showNameAiDialog) {
        AiNameRecommendationDialog(
            prompt = prompt,
            onDismiss = { showNameAiDialog = false },
            onNameSelected = { name = it }
        )
    }
}
