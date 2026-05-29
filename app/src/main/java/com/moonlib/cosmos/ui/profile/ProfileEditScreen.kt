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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.profile.CharacterProfile
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
    isPlayer: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (CharacterProfile) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark
    val isEditMode = initialProfile != null

    // ── 核心输入状态 ──────────────────────────────────────────
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var prompt by remember { mutableStateOf(initialProfile?.prompt ?: "") }

    // ── 对话框显示状态 ────────────────────────────────────────
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ── 检查是否有未保存的改动 ────────────────────────────────
    val hasChanges = remember(name, prompt, initialProfile) {
        val originalName = initialProfile?.name ?: ""
        val originalPrompt = initialProfile?.prompt ?: ""
        name != originalName || prompt != originalPrompt
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
                    // 姓名输入
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("姓名") },
                        placeholder = { Text("请输入真实姓名或角色名") },
                        singleLine = true,
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
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("人设提示词") },
                            placeholder = { Text("请详细输入该人设的性格特征、身世背景、口吻喜好等核心提示词...") },
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
                }
            }

            // ── 3. 保存动作按键 ─────────────────────────────────────
            Button(
                onClick = {
                    if (isFormValid) {
                        val finalProfile = CharacterProfile(
                            id = initialProfile?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            prompt = prompt.trim(),
                            isPlayer = isPlayer
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
}
