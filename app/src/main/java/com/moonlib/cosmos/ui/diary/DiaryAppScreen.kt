package com.moonlib.cosmos.ui.diary

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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
import com.moonlib.cosmos.data.diary.DiaryEngine
import com.moonlib.cosmos.data.diary.DiaryEntry
import com.moonlib.cosmos.data.diary.DiaryRepository
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository
import com.moonlib.cosmos.ui.chat.AvatarView
import kotlinx.coroutines.launch

/**
 * 日记 APP 主界面
 *
 * 职责单一：负责渲染倒序排布的精致日记列表、独立的状态卡人物 Tab 切换区、@角色选择框、输入法自适应面板，以及人称和状态卡开启设置 Dialog。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 仓库实例化
    val diaryRepo = remember { DiaryRepository(context) }
    val profileRepo = remember { CharacterProfileRepository(context) }
    val interactionSettingsRepo = remember { InteractionSettingsRepository(context) }

    // 数据状态管理
    var diaryList by remember { mutableStateOf(diaryRepo.getDiaries().reversed()) }
    val characterProfiles = remember { profileRepo.getProfiles() }
    val availableCharacters = remember(characterProfiles) { characterProfiles.filter { !it.isPlayer } }

    // 控制设置状态
    var isSettingDialogOpen by remember { mutableStateOf(false) }
    var isStatusCardEnabled by remember { mutableStateOf(interactionSettingsRepo.isDiaryStatusCardEnabled()) }
    var perspective by remember { mutableStateOf(diaryRepo.getPerspective()) }

    // 输入面板状态
    var textInput by remember { mutableStateOf("") }
    var selectedCharacterIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isAtDialogOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "日记",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isSettingDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 1. 日记列表区域 (最新的日记在最上方) ──
                if (diaryList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "✍️ 暂无日记记录",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "在下方 @ 角色，输入今天的故事发生起因吧",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(diaryList, key = { it.id }) { diary ->
                            DiaryCard(
                                diary = diary,
                                characterProfiles = characterProfiles,
                                isStatusCardEnabled = isStatusCardEnabled,
                                onDelete = {
                                    diaryRepo.deleteDiary(diary.id)
                                    diaryList = diaryRepo.getDiaries().reversed()
                                    Toast.makeText(context, "日记已删除", Toast.LENGTH_SHORT).show()
                                },
                                onRegenerate = {
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val currentDiaries = diaryRepo.getDiaries()
                                            val index = currentDiaries.indexOfFirst { it.id == diary.id }
                                            val contextDiaries = if (index != -1) currentDiaries.take(index) else currentDiaries
                                            
                                            val newEntry = DiaryEngine.generateDiary(
                                                context = context,
                                                playerInput = diary.playerInput,
                                                involvedCharacterIds = diary.involvedCharacterIds,
                                                diariesContextOverride = contextDiaries
                                            )
                                            
                                            val updatedList = currentDiaries.toMutableList()
                                            val idx = updatedList.indexOfFirst { it.id == diary.id }
                                            if (idx != -1) {
                                                updatedList[idx] = newEntry.copy(timestamp = diary.timestamp)
                                                diaryRepo.saveDiaries(updatedList)
                                                diaryList = diaryRepo.getDiaries().reversed()
                                                Toast.makeText(context, "日记已重新生成！", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "重新生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // ── 2. 底栏控制与输入区域 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 已选择角色标签展示Row
                    if (selectedCharacterIds.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "参与者:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    selectedCharacterIds.forEach { charId ->
                                        val char = characterProfiles.firstOrNull { it.id == charId }
                                        if (char != null) {
                                            InputCharacterTag(
                                                character = char,
                                                onRemove = {
                                                    selectedCharacterIds = selectedCharacterIds.filter { it != charId }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 输入框与发送行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // @ 按钮
                        IconButton(
                            onClick = { isAtDialogOpen = true },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = "选择人物",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 文字输入框
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    text = "在此输入今天发生的事或心情感悟...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        // 发送按钮 (带 Loading)
                        IconButton(
                            onClick = {
                                val input = textInput.trim()
                                if (input.isBlank()) {
                                    Toast.makeText(context, "请输入起因引子", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                if (selectedCharacterIds.isEmpty()) {
                                    Toast.makeText(context, "请点击 @ 按钮选择至少一位参与人物", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                isLoading = true
                                textInput = ""
                                coroutineScope.launch {
                                    try {
                                        val newEntry = DiaryEngine.generateDiary(
                                            context = context,
                                            playerInput = input,
                                            involvedCharacterIds = selectedCharacterIds
                                        )
                                        diaryRepo.addDiary(newEntry)
                                        diaryList = diaryRepo.getDiaries().reversed()
                                        selectedCharacterIds = emptyList()
                                        Toast.makeText(context, "日记记录成功！", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                                        textInput = input // 恢复内容
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "发送",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 3. 人称与状态卡配置 Dialog ──
    if (isSettingDialogOpen) {
        AlertDialog(
            onDismissRequest = { isSettingDialogOpen = false },
            title = {
                Text(
                    text = "日记设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 人称选择
                    Column {
                        Text(
                            text = "叙事人称",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = perspective == "first",
                                onClick = {
                                    perspective = "first"
                                    diaryRepo.setPerspective("first")
                                }
                            )
                            Text(text = "第一人称 (以“我”视角创作)", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = perspective == "third",
                                onClick = {
                                    perspective = "third"
                                    diaryRepo.setPerspective("third")
                                }
                            )
                            Text(text = "第三人称 (上帝/旁观视角创作)", fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // 状态卡开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "日记状态卡",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "开启后，AI 将在生成日记时根据剧情更新并展示所有人的状态卡。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = isStatusCardEnabled,
                            onCheckedChange = { checked ->
                                isStatusCardEnabled = checked
                                interactionSettingsRepo.setDiaryStatusCardEnabled(checked)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isSettingDialogOpen = false }) {
                    Text("完成", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── 4. @ 角色多选 Dialog ──
    if (isAtDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAtDialogOpen = false },
            title = {
                Text(
                    text = "选择参与本篇日记的角色",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (availableCharacters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "暂无可用角色档案，请先在档案 APP 中创建角色", fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableCharacters) { char ->
                            val isSelected = selectedCharacterIds.contains(char.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedCharacterIds = if (isSelected) {
                                            selectedCharacterIds.filter { it != char.id }
                                        } else {
                                            selectedCharacterIds + char.id
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarView(
                                    avatarPath = char.avatar,
                                    name = char.name,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = char.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedCharacterIds = if (checked == true) {
                                            selectedCharacterIds + char.id
                                        } else {
                                            selectedCharacterIds.filter { it != char.id }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { isAtDialogOpen = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确定", color = Color.White)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * 底部已勾选的参与人物 Chip Tag
 */
@Composable
fun InputCharacterTag(
    character: CharacterProfile,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AvatarView(
            avatarPath = character.avatar,
            name = character.name,
            size = 16.dp
        )
        Text(
            text = character.name,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "移除",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier
                .size(12.dp)
                .clickable { onRemove() }
        )
    }
}

/**
 * 日记信纸感时间线卡片
 */
@Composable
fun DiaryCard(
    diary: DiaryEntry,
    characterProfiles: List<CharacterProfile>,
    isStatusCardEnabled: Boolean,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit
) {
    // 状态卡激活的人称头像Tab切换
    var activeTabCharId by remember { mutableStateOf(diary.involvedCharacterIds.firstOrNull()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶栏：虚拟时间 + 删除
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diary.virtualTime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )

                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("重新生成", fontSize = 13.sp) },
                            onClick = {
                                menuExpanded = false
                                onRegenerate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 用户发送的引子/起因
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🌱 引子: ${diary.playerInput}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 日记正文
            Text(
                text = diary.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 参与人小头像Row + 状态卡渲染
            val involvedChars = remember(diary.involvedCharacterIds, characterProfiles) {
                characterProfiles.filter { diary.involvedCharacterIds.contains(it.id) }
            }

            if (involvedChars.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 人物列表标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        involvedChars.forEach { char ->
                            AvatarView(
                                avatarPath = char.avatar,
                                name = char.name,
                                size = 22.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "参与剧情",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }

                // 底部状态卡逻辑 (当本篇日记里存有状态快照，且应用开启了状态卡显示时)
                val hasStatusSnapshot = diary.statusMap.isNotEmpty()
                if (isStatusCardEnabled && hasStatusSnapshot) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 状态卡人物 Tab 切换丸按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        involvedChars.forEach { char ->
                            val isActive = activeTabCharId == char.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    )
                                    .clickable { activeTabCharId = char.id }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AvatarView(
                                    avatarPath = char.avatar,
                                    name = char.name,
                                    size = 14.dp
                                )
                                Text(
                                    text = char.name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 展示所选角色的状态属性快照网格
                    val selectedCharId = activeTabCharId ?: diary.involvedCharacterIds.firstOrNull()
                    val charStatus = diary.statusMap[selectedCharId]

                    if (charStatus != null && charStatus.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            charStatus.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "📍 $key: ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.width(70.dp)
                                    )
                                    Text(
                                        text = value,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        lineHeight = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "该角色在本篇日记中状态未变更",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
