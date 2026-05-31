package com.moonlib.cosmos.ui.diary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.diary.DiaryEngine
import com.moonlib.cosmos.data.diary.DiaryRepository
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.interaction.InteractionSettingsRepository
import kotlinx.coroutines.launch

/**
 * 日记 APP 主界面
 *
 * 职责单一：负责组织和调度日记列表页面、底层控制输入区、设置对话框及 @ 角色选择弹窗的顶层控制流。
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

    BackHandler(enabled = true) {
        onGoBack()
    }

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
                .imePadding()
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
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
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
                DiaryInputBar(
                    textInput = textInput,
                    onTextInputChange = { textInput = it },
                    selectedCharacterIds = selectedCharacterIds,
                    characterProfiles = characterProfiles,
                    isLoading = isLoading,
                    onRemoveCharacter = { charId ->
                        selectedCharacterIds = selectedCharacterIds.filter { it != charId }
                    },
                    onAtClicked = { isAtDialogOpen = true },
                    onSendClicked = {
                        val input = textInput.trim()
                        if (input.isBlank()) return@DiaryInputBar
                        if (selectedCharacterIds.isEmpty()) return@DiaryInputBar
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
                            } catch (e: Exception) {
                                e.printStackTrace()
                                textInput = input // 恢复内容
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            }
        }
    }

    // ── 3. 人称与状态卡配置 Dialog ──
    if (isSettingDialogOpen) {
        DiarySettingsDialog(
            onDismissRequest = { isSettingDialogOpen = false },
            perspective = perspective,
            onPerspectiveChange = { nextPerspective ->
                perspective = nextPerspective
                diaryRepo.setPerspective(nextPerspective)
            },
            isStatusCardEnabled = isStatusCardEnabled,
            onStatusCardEnabledChange = { checked ->
                isStatusCardEnabled = checked
                interactionSettingsRepo.setDiaryStatusCardEnabled(checked)
            }
        )
    }

    // ── 4. @ 角色多选 Dialog ──
    if (isAtDialogOpen) {
        DiaryAtCharacterDialog(
            onDismissRequest = { isAtDialogOpen = false },
            availableCharacters = availableCharacters,
            selectedCharacterIds = selectedCharacterIds,
            onSelectedCharactersChange = { nextSelection ->
                selectedCharacterIds = nextSelection
            }
        )
    }
}
