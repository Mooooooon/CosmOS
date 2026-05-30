package com.moonlib.cosmos.ui.chat

import android.widget.Toast
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.chat.ChatEngine
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.ui.common.conversationContentImeResize
import com.moonlib.cosmos.ui.common.conversationInputInsets
import com.moonlib.cosmos.ui.common.rememberImeVisible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天会话对话界面
 *
 * 职责单一：负责单次会话的历史记录渲染、用户输入发送以及 AI 异步回复的状态转换与滚动控制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    contactId: String,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chatRepo = remember { ChatRepository(context) }

    // 1. 获取联系人详情与用户个人配置
    val contact = remember(contactId) {
        chatRepo.getContacts().firstOrNull { it.id == contactId }
    }
    
    if (contact == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未找到该密友", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    val userNickname = remember { chatRepo.getUserNickname() }
    val userAvatar = remember { chatRepo.getUserAvatar() }

    // 2. 状态管理：消息列表、输入框、AI输入状态
    var messages by remember { mutableStateOf(chatRepo.getMessages(contactId)) }
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

    // 首次载入自动置底 (不带动画，秒开最自然)
    LaunchedEffect(messages.size) {
        scrollToBottom(false)
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            delay(250)
            scrollToBottom(false)
        }
    }

    // 消息发送核心方法
    val handleSend: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotBlank() && !isAiGenerating) {
            inputText = ""
            
            // 2.1 获取当前的虚拟时间，作为用户消息的时间戳
            val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
            
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                senderId = "user",
                content = text,
                timestamp = currentVirtualTime
            )
            chatRepo.saveMessage(contactId, userMsg)
            
            // 发送消息后，我们人为向前微调虚拟时间 15 秒（代表打字与发送的动作耗时）
            VirtualTimeManager.updateTime(currentVirtualTime + 15000L)
            
            messages = chatRepo.getMessages(contactId) // 实时刷新 UI
            
            // 2.2 自动置底
            scrollToBottom(true)

            // 2.3 开启协程触发 AI 回复
            isAiGenerating = true
            coroutineScope.launch {
                try {
                    // 模拟网络延迟输入，使“对方正在输入”动画状态更真实
                    delay(800)
                    
                    // 调用 AI 聊天引擎（引擎在内部分析、保存并推进时间）
                    ChatEngine.getAiResponse(context, contact)
                    
                    // 刷新消息列表
                    messages = chatRepo.getMessages(contactId)
                    scrollToBottom(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    
                    // 保存一个系统级假报错消息渲染在左侧，保障健壮性
                    val errorMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = "system",
                        content = "【系统提示】: ${e.localizedMessage ?: "AI 服务暂时开小差啦，请在系统设置中确认 AI 密钥。"}",
                        timestamp = VirtualTimeManager.getCurrentTimeMillis()
                    )
                    chatRepo.saveMessage(contactId, errorMsg)
                    messages = chatRepo.getMessages(contactId)
                    scrollToBottom(true)
                } finally {
                    isAiGenerating = false
                }
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
                            text = contact.nickname,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        if (isAiGenerating) {
                            Text(
                                text = "对方正在输入...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        } else {
                            Text(
                                text = "手机在线",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ConversationInputBar(
                inputText = inputText,
                isAiGenerating = isAiGenerating,
                placeholder = "聊点什么吧...",
                onInputChange = { inputText = it },
                onSend = handleSend
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
            
            // ─── 3. 消息气泡对话区 ────────────────────────────────────
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
                    // 3.1 聚合时间戳显示：如果上一条消息与本条时间差超过 3 分钟，显示时间戳
                    val showTimeLabel = if (index == 0) {
                        true
                    } else {
                        val prevMsg = messages[index - 1]
                        msg.timestamp - prevMsg.timestamp > 3 * 60 * 1000 // 3 分钟
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

                        // 3.2 渲染气泡
                        when (msg.senderId) {
                            "user" -> {
                                UserMessageRow(
                                    content = msg.content,
                                    userNickname = userNickname,
                                    userAvatar = userAvatar,
                                    onDelete = {
                                        chatRepo.deleteMessage(contactId, msg.id)
                                        messages = chatRepo.getMessages(contactId)
                                        Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show()
                                    },
                                    onResend = {
                                        // 1. 回调系统时间到这条消息发送的时间
                                        VirtualTimeManager.rollbackTime(msg.timestamp)
                                        
                                        // 2. 清空这条消息后面的消息
                                        chatRepo.deleteMessagesAfter(contactId, msg.id)
                                        messages = chatRepo.getMessages(contactId)
                                        
                                        // 3. 重新发送ai请求
                                        isAiGenerating = true
                                        coroutineScope.launch {
                                            try {
                                                delay(800)
                                                ChatEngine.getAiResponse(context, contact)
                                                messages = chatRepo.getMessages(contactId)
                                                scrollToBottom(true)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                val errorMsg = ChatMessage(
                                                    id = UUID.randomUUID().toString(),
                                                    senderId = "system",
                                                    content = "【系统提示】: ${e.localizedMessage ?: "AI 服务暂时开小差啦，请在系统设置中确认 AI 密钥。"}",
                                                    timestamp = VirtualTimeManager.getCurrentTimeMillis()
                                                )
                                                chatRepo.saveMessage(contactId, errorMsg)
                                                messages = chatRepo.getMessages(contactId)
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
                                        chatRepo.deleteMessage(contactId, msg.id)
                                        messages = chatRepo.getMessages(contactId)
                                        Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            else -> {
                                ContactMessageRow(
                                    content = msg.content,
                                    contact = contact,
                                    onDelete = {
                                        chatRepo.deleteMessage(contactId, msg.id)
                                        messages = chatRepo.getMessages(contactId)
                                        Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show()
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
private fun ConversationInputBar(
    inputText: String,
    isAiGenerating: Boolean,
    placeholder: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
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
                placeholder = { Text(placeholder) },
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

            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isAiGenerating,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (inputText.isNotBlank() && !isAiGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (inputText.isNotBlank() && !isAiGenerating)
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
 * 时间戳标签
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
 * 用户消息气泡 Row (右侧排列)
 */
@Composable
private fun UserMessageRow(
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
        // 气泡卡片用 Box 包裹以承载 DropdownMenu
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
                    text = content,
                    color = Color.White,
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
                    text = { Text("重新发送", color = MaterialTheme.colorScheme.primary) },
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
 * 联系人消息气泡 Row (左侧排列)
 */
@Composable
private fun ContactMessageRow(
    content: String,
    contact: com.moonlib.cosmos.data.chat.ChatContact,
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
        // 联系人头像
        AvatarView(
            avatarPath = contact.avatar,
            name = contact.nickname,
            size = 40.dp
        )

        // 气泡卡片用 Box 包裹以承载 DropdownMenu
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
                    text = content,
                    color = MaterialTheme.colorScheme.onSurface,
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
 * 系统级报错提示消息 Row (居中灰框)
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
