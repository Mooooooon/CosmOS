package com.moonlib.cosmos.ui.interaction

// import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.interaction.InteractionEngine
import com.moonlib.cosmos.data.interaction.InteractionMessage
import com.moonlib.cosmos.data.interaction.InteractionRepository
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.ui.chat.AvatarView
import com.moonlib.cosmos.ui.common.conversationContentImeResize
import com.moonlib.cosmos.ui.common.conversationInputInsets
import com.moonlib.cosmos.ui.common.rememberImeVisible
import com.moonlib.cosmos.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
    val character = remember(characterId) {
        profileRepo.getProfiles().firstOrNull { it.id == characterId }
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

            // 获取当前的虚拟时间，作为用户实体互动的开始时间
            val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()

            val userMsg = InteractionMessage(
                id = UUID.randomUUID().toString(),
                senderId = "user",
                content = text,
                timestamp = currentVirtualTime,
                statusMap = settingsRepo.getCharacterStatus(characterId)
            )
            interactionRepo.saveMessage(characterId, userMsg)

            // 实体互动开始，我们向前微调虚拟时间 15 秒（代表肢体动作与语言表达的间隔）
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${character.name} 的当前状态",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // 流式排列状态词条
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (key in statusKeys) {
                                val rawValue = charStatus[key.name] ?: "-"
                                val value = remember(rawValue) {
                                    if (rawValue == "-") {
                                        "-"
                                    } else {
                                        rawValue.replace(Regex("[()（）]"), "").trim()
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${key.name}: ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = value,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                                        interactionRepo.deleteMessage(characterId, msg.id)
                                        messages = interactionRepo.getMessages(characterId)
                                        // Toast.makeText(context, "互动已删除", Toast.LENGTH_SHORT).show()
                                    },
                                    onResend = {
                                        // 1. 回调系统虚拟时间
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
                                            val statusToRestore = lastInteractionMsg.statusMap ?: emptyMap()
                                            settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                        } else {
                                            if (lastDiary!!.timestamp > lastInteractionMsg!!.timestamp) {
                                                val statusToRestore = lastDiary.statusMap[characterId] ?: emptyMap()
                                                settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                            } else {
                                                val statusToRestore = lastInteractionMsg.statusMap ?: emptyMap()
                                                settingsRepo.saveCharacterStatus(characterId, statusToRestore)
                                            }
                                        }

                                        // 2. 清空本消息之后的记录
                                        interactionRepo.deleteMessagesAfter(characterId, msg.id)
                                        messages = interactionRepo.getMessages(characterId)

                                        // 3. 触发重发
                                        isAiGenerating = true
                                        coroutineScope.launch {
                                            try {
                                                delay(1000)
                                                InteractionEngine.getAiResponse(context, characterId)
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
                                        interactionRepo.deleteMessage(characterId, msg.id)
                                        messages = interactionRepo.getMessages(characterId)
                                        // Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            else -> {
                                CharacterInteractionRow(
                                    messageId = msg.id,
                                    content = msg.content,
                                    characterName = character.name,
                                    characterAvatar = character.avatar,
                                    voicePlaybackState = voicePlaybackState,
                                    onDelete = {
                                        interactionRepo.deleteMessage(characterId, msg.id)
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

@Composable
private fun InteractionInputBar(
    inputText: String,
    isAiGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onLongSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .conversationInputInsets()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("说点什么吧...") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            )

            val isEnabled = inputText.isNotBlank() && !isAiGenerating
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    .pointerInput(isEnabled) {
                        if (isEnabled) {
                            detectTapGestures(
                                onTap = { onSend() },
                                onLongPress = { onLongSend() }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (isEnabled)
                        Color.White
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 实体动作括号匹配高对比度富文本着色解析器 (核心亮点)
 */
@Composable
private fun formatInteractionContent(text: String, isUser: Boolean): androidx.compose.ui.text.AnnotatedString {
    val themeConfig = LocalThemeConfig.current
    val isDark = themeConfig.isDark

    // 对括弧内的动作文字进行特殊样式强调
    // 用户气泡本身为 Primary 主体色，所以动作文本采用 75% 的半透明白，拉开层次
    // 角色气泡为普通灰黑背景，所以动作文本直接采用系统 primary 强调色渲染
    val actionColor = if (isUser) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    }

    val speechColor = if (isUser) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    return buildAnnotatedString {
        var cursor = 0
        // 正则表达式高度兼容中文小括号（）与英文小括号 ()
        val regex = """[（(][^）)]*[）)]""".toRegex()
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            // 1. 渲染普通语言对白
            if (start > cursor) {
                withStyle(SpanStyle(color = speechColor, fontWeight = FontWeight.Normal)) {
                    append(text.substring(cursor, start))
                }
            }

            // 2. 渲染带括弧的动作描述（使用斜体加独立对比色展现）
            withStyle(
                SpanStyle(
                    color = actionColor,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(text.substring(start, end))
            }

            cursor = end
        }

        // 3. 渲染末尾剩余的普通语言对白
        if (cursor < text.length) {
            withStyle(SpanStyle(color = speechColor, fontWeight = FontWeight.Normal)) {
                append(text.substring(cursor))
            }
        }
    }
}

/**
 * 虚拟世界时间标签
 */
@Composable
private fun TimeLabel(
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val timeStr = remember(timestamp) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    }
    Text(
        text = timeStr,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * 用户的实体动作气泡行 (右侧排列)
 */
@Composable
private fun UserInteractionRow(
    content: String,
    userNickname: String,
    userAvatar: String,
    onDelete: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            showMenu = true
                        }
                    )
                }
            ) {
                Text(
                    text = formatInteractionContent(content, isUser = true),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 22.sp
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("重新互动 (回滚系统虚拟时间)", color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        showMenu = false
                        onResend()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }

        // 用户头像
        AvatarView(
            avatarPath = userAvatar,
            name = userNickname,
            size = 40.dp
        )
    }
}

/**
 * 角色实体的动作气泡行 (左侧排列)
 */
@Composable
private fun CharacterInteractionRow(
    messageId: String,
    content: String,
    characterName: String,
    characterAvatar: String,
    voicePlaybackState: InteractionVoicePlaybackState,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 48.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // 角色头像
        AvatarView(
            avatarPath = characterAvatar,
            name = characterName,
            size = 40.dp
        )

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            showMenu = true
                        }
                    )
                }
            ) {
                Text(
                    text = formatInteractionContent(content, isUser = false),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 22.sp
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (voicePlaybackState.isAvailable) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when {
                                    voicePlaybackState.isLoading -> "正在生成语音..."
                                    voicePlaybackState.isPlaying -> "停止播放"
                                    else -> voicePlaybackState.statusText ?: "播放语音"
                                },
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            voicePlaybackState.play(messageId, content)
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * 实体交互报错气泡提示 Row
 */
@Composable
private fun SystemMessageRow(
    content: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            showMenu = true
                        }
                    )
                }
            ) {
                Text(
                    text = content,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * 莫兰迪色系的自适应背景生成函数
 */
private fun getMorandiColor(name: String, isDark: Boolean): Color {
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
    return colors[Math.abs(hash) % colors.size]
}
