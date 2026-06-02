package com.moonlib.cosmos.ui.interaction

// import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.interaction.InteractionEngine
import com.moonlib.cosmos.data.interaction.InteractionMessage
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.interaction.MultiInteractionEngine
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.ui.common.conversationContentImeResize
import com.moonlib.cosmos.ui.common.rememberImeVisible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * 实体动作互动会话界面
 *
 * 职责单一：负责单次实体互动的历史记录渲染、用户输入发送以及 AI 异步回复的状态转换与滚动控制。
 * 核心亮点：使用正则表达式解析带有括号的文本，将动作部分与说话部分进行高对比度、不同样式的混合渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionConversationScreen(
    characterId: String,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val interactionRepo = remember { InteractionRepository(context) }
    val profileRepo = remember { CharacterProfileRepository(context) }
    val chatRepo = remember { com.moonlib.cosmos.data.chat.ChatRepository(context) }

    // 1. 获取对应的角色档案人设
    val allProfiles = remember { profileRepo.getProfiles() }
    val profilesById = remember(allProfiles) { allProfiles.associateBy { it.id } }
    val character = remember(characterId, allProfiles) {
        allProfiles.firstOrNull { it.id == characterId }
    }

    if (character == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未找到该角色档案", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    // 2. 加载用户自身资料
    val userNickname = remember { chatRepo.getUserNickname() }
    val userAvatar = remember { chatRepo.getUserAvatar() }
    val voicePlaybackState = rememberInteractionVoicePlaybackState(characterId)

    // 加载并 observe 状态卡全局及各个角色当前状态数据
    val settingsRepo = remember { com.moonlib.cosmos.data.interaction.InteractionSettingsRepository(context) }
    var isStatusCardEnabled by remember { mutableStateOf(settingsRepo.isStatusCardEnabled()) }
    var statusKeys by remember { mutableStateOf(settingsRepo.getStatusKeys()) }
    var charStatus by remember { mutableStateOf(settingsRepo.getCharacterStatus(characterId)) }

    // 3. 状态管理：消息列表、输入框、AI 输入生成状态
    var messages by remember { mutableStateOf(interactionRepo.getMessages(characterId)) }
    var inputText by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }
    val isImeVisible = rememberImeVisible()

    // 自动滑动到底部的核心方法
    val scrollToBottom: (Boolean) -> Unit = { smooth ->
        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                if (smooth) {
                    listState.animateScrollToItem(messages.size - 1)
                } else {
                    listState.scrollToItem(messages.size - 1)
                }
            }
        }
    }

    // 首次载入及消息量刷新时，拉取最新的状态卡配置和状态值
    LaunchedEffect(messages.size) {
        isStatusCardEnabled = settingsRepo.isStatusCardEnabled()
        statusKeys = settingsRepo.getStatusKeys()
        charStatus = settingsRepo.getCharacterStatus(characterId)
        scrollToBottom(false)
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            delay(250)
            scrollToBottom(false)
        }
    }

    // 实体互动发送消息核心方法
    val handleSend: (Boolean) -> Unit = { triggerAi ->
        val text = inputText.trim()
        if (text.isNotBlank() && !isAiGenerating) {
            inputText = ""

            // 获取当前系统时间，作为用户实体互动的开始时间
            val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()

            val userMsg = InteractionMessage(
                id = UUID.randomUUID().toString(),
                senderId = "user",
                content = text,
                timestamp = currentVirtualTime,
                statusMap = settingsRepo.getCharacterStatus(characterId)
            )
            interactionRepo.saveMessage(characterId, userMsg)

            // 实体互动开始，我们向前微调系统时间 15 秒（代表肢体动作与语言表达的间隔）
            VirtualTimeManager.updateTime(currentVirtualTime + 15000L)

            messages = interactionRepo.getMessages(characterId) // 刷新 UI

            scrollToBottom(true)

            if (triggerAi) {
                // 开启协程触发 AI 回复
                isAiGenerating = true
                coroutineScope.launch {
                    try {
                        // 模拟实体面对面的思考对白动作延迟
                        delay(1000)

                        // 调用实体互动 AI 引擎（引擎内部分析、保存并推进时间）
                        InteractionEngine.getAiResponse(context, characterId)

                        // 刷新消息列表
                        messages = interactionRepo.getMessages(characterId)
                        scrollToBottom(true)
                    } catch (e: Exception) {
                        e.printStackTrace()

                        // 保存一个系统级假报错消息渲染在中央，保障健壮性
                        val errorMsg = InteractionMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = "system",
                            content = "【系统提示】: ${e.localizedMessage ?: "AI 服务暂时开小差啦，请在系统设置中确认 AI 密钥。"}",
                            timestamp = VirtualTimeManager.getCurrentTimeMillis()
                        )
                        interactionRepo.saveMessage(characterId, errorMsg)
                        messages = interactionRepo.getMessages(characterId)
                        scrollToBottom(true)
                    } finally {
                        isAiGenerating = false
                    }
                }
            } else {
                // Toast.makeText(context, "已发送至互动记录 (未触发 AI 回复)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Column {
                        Text(
                            text = character.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        if (isAiGenerating) {
                            Text(
                                text = "对方正在回应...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        } else {
                            Text(
                                text = "面对面互动中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            InteractionInputBar(
                inputText = inputText,
                isAiGenerating = isAiGenerating,
                onInputChange = { inputText = it },
                onSend = { handleSend(true) },
                onLongSend = { handleSend(false) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .conversationContentImeResize()
        ) {
            // ─── 0. 角色实体状态卡展示面板 ─────────────────────────────────────
            if (isStatusCardEnabled && statusKeys.isNotEmpty()) {
                InteractionStatusCard(
                    characterName = character.name,
                    statusKeys = statusKeys,
                    charStatus = charStatus
                )
            }

            // ─── 1. 实体动作消息渲染区 ─────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id }
                ) { index, msg ->
                    // 如果上一条消息与本条时间差超过 3 分钟，显示时间戳
                    val showTimeLabel = if (index == 0) {
                        true
                    } else {
                        val prevMsg = messages[index - 1]
                        msg.timestamp - prevMsg.timestamp > 3 * 60 * 1000
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (showTimeLabel) {
                            TimeLabel(
                                timestamp = msg.timestamp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }

                        // 根据发送方进行左右气泡渲染
                        when (msg.senderId) {
                            "user" -> {
                                UserInteractionRow(
                                    content = msg.content,
                                    userNickname = userNickname,
                                    userAvatar = userAvatar,
                                    onDelete = {
                                        if (msg.participantIds.isNotEmpty()) {
                                            interactionRepo.deleteSharedMessage(msg)
                                        } else {
                                            interactionRepo.deleteMessage(characterId, msg.id)
                                        }
                                        messages = interactionRepo.getMessages(characterId)
                                        // Toast.makeText(context, "互动已删除", Toast.LENGTH_SHORT).show()
                                    },
                                    onResend = {
                                        VirtualTimeManager.rollbackTime(msg.timestamp)

                                        // 回滚状态卡片到上一条（检查上一条最新的状态卡消息是日记还是互动）
                                        val targetTime = msg.timestamp
                                        val lastInteractionMsg = interactionRepo.getMessages(characterId)
                                            .filter { it.timestamp <= targetTime && it.statusMap != null }
                                            .maxByOrNull { it.timestamp }
                                        
                                        val diaryRepo = com.moonlib.cosmos.data.diary.DiaryRepository(context)
                                        val lastDiary = diaryRepo.getDiaries()
                                            .filter { it.timestamp <= targetTime && it.statusMap.containsKey(characterId) }
                                            .maxByOrNull { it.timestamp }

                                        if (lastDiary == null && lastInteractionMsg == null) {
                                            settingsRepo.saveCharacterStatus(characterId, emptyMap())
                                        } else if (lastDiary != null && lastInteractionMsg == null) {
                                            val statusToRestore = lastDiary.statusMap[characterId] ?: emptyMap()
                                            settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                        } else if (lastDiary == null && lastInteractionMsg != null) {
                                            val statusToRestore = lastInteractionMsg.statusMapByCharacterId?.get(characterId)
                                                ?: lastInteractionMsg.statusMap
                                                ?: emptyMap()
                                            settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                        } else {
                                            if (lastDiary!!.timestamp > lastInteractionMsg!!.timestamp) {
                                                val statusToRestore = lastDiary.statusMap[characterId] ?: emptyMap()
                                                settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                            } else {
                                                val statusToRestore = lastInteractionMsg.statusMapByCharacterId?.get(characterId)
                                                    ?: lastInteractionMsg.statusMap
                                                    ?: emptyMap()
                                                settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                            }
                                        }

                                        if (msg.participantIds.isNotEmpty()) {
                                            interactionRepo.deleteSharedMessagesAfter(msg)
                                        } else {
                                            interactionRepo.deleteMessagesAfter(characterId, msg.id)
                                        }
                                        messages = interactionRepo.getMessages(characterId)

                                        isAiGenerating = true
                                        coroutineScope.launch {
                                            try {
                                                delay(1000)
                                                if (msg.participantIds.size >= 2) {
                                                    MultiInteractionEngine.getAiResponse(context, msg.participantIds)
                                                } else {
                                                    InteractionEngine.getAiResponse(context, characterId)
                                                }
                                                messages = interactionRepo.getMessages(characterId)
                                                scrollToBottom(true)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                val errorMsg = InteractionMessage(
                                                    id = UUID.randomUUID().toString(),
                                                    senderId = "system",
                                                    content = "【系统提示】: ${e.localizedMessage ?: "AI 服务暂时开小差啦，请在系统设置中确认 AI 密钥。"}",
                                                    timestamp = VirtualTimeManager.getCurrentTimeMillis()
                                                )
                                                interactionRepo.saveMessage(characterId, errorMsg)
                                                messages = interactionRepo.getMessages(characterId)
                                                scrollToBottom(true)
                                            } finally {
                                                isAiGenerating = false
                                            }
                                        }
                                    }
                                )
                            }
                            "system" -> {
                                SystemMessageRow(
                                    content = msg.content,
                                    onDelete = {
                                        if (msg.participantIds.isNotEmpty()) {
                                            interactionRepo.deleteSharedMessage(msg)
                                        } else {
                                            interactionRepo.deleteMessage(characterId, msg.id)
                                        }
                                        messages = interactionRepo.getMessages(characterId)
                                        // Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            else -> {
                                val senderProfile = profilesById[msg.senderId] ?: character
                                CharacterInteractionRow(
                                    messageId = msg.id,
                                    content = msg.content,
                                    characterName = senderProfile.name,
                                    characterAvatar = senderProfile.avatar,
                                    voicePlaybackState = if (senderProfile.id == characterId) voicePlaybackState else null,
                                    onDelete = {
                                        if (msg.participantIds.isNotEmpty()) {
                                            interactionRepo.deleteSharedMessage(msg)
                                        } else {
                                            interactionRepo.deleteMessage(characterId, msg.id)
                                        }
                                        messages = interactionRepo.getMessages(characterId)
                                        // Toast.makeText(context, "互动已删除", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
