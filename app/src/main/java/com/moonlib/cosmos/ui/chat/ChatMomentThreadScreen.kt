package com.moonlib.cosmos.ui.chat

// import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.chat.Moment
import com.moonlib.cosmos.data.chat.MomentEngine
import com.moonlib.cosmos.data.chat.MomentRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 朋友圈动态评论区盖楼详情页面
 * 
 * 职责单一：渲染选中的根动态、递归构建并缩进显示嵌套评论回复树、追踪特定节点回复状态、向后台安全触发 AI 发帖及回复刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMomentThreadScreen(
    momentId: String,
    repository: MomentRepository,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var repliesList by remember { mutableStateOf(repository.getMoments()) }
    var textInput by remember { mutableStateOf("") }

    val rootMoment = remember(repliesList, momentId) {
        repliesList.firstOrNull { it.id == momentId }
    }

    if (rootMoment == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("动态已不存在或已被删除", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    // 追踪当前选定的回复节点（为 null 代表评论主动态，否则代表回复特定的 NPC 评论）
    var selectedReplyNode by remember { mutableStateOf<Moment?>(null) }
    var isLoadingReplies by remember { mutableStateOf(false) }

    // 递归构建缩进回复树
    val threadItems = remember(repliesList, momentId) {
        val result = mutableListOf<Pair<Moment, Int>>() // Pair<Moment, Depth>
        
        fun buildTree(parentId: String, depth: Int) {
            val children = repliesList.filter { it.parentId == parentId }.sortedBy { it.timestamp }
            for (child in children) {
                result.add(Pair(child, depth))
                buildTree(child.id, depth + 1) // 递归加载评论树，缩进深度递增
            }
        }

        buildTree(momentId, 0)
        result
    }

    // 系统物理返回手势安全拦截
    BackHandler {
        onGoBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "动态正文及评论",
                        fontSize = 17.sp,
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
                // ── 1. 滚动视图区（主发帖卡片 + 回复楼层列表） ─────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 主发帖卡片
                    item {
                        ThreadRootCard(
                            moment = rootMoment,
                            repository = repository,
                            onDelete = { toDelete ->
                                repository.deleteMoment(toDelete.id)
                                onGoBack() // 根帖子删除了，安全退回上一级
                                // Toast.makeText(context, "动态已成功删除", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 评论列表
                    if (threadItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "💬 暂无评论。快来发表你的想法吧！",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            }
                        }
                    } else {
                        items(threadItems, key = { it.first.id }) { (reply, depth) ->
                            ThreadReplyItem(
                                reply = reply,
                                depth = depth,
                                repository = repository,
                                onReplyClick = { selectedReplyNode = reply },
                                onDelete = { toDelete ->
                                    repository.deleteMoment(toDelete.id)
                                    repliesList = repository.getMoments() // 刷新列表
                                    // Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // ── 2. 回复发送状态指示器 ─────────────
                AnimatedVisibility(
                    visible = selectedReplyNode != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    selectedReplyNode?.let { node ->
                        val nodeAuthor = repository.getProfile(node.authorId)
                        val name = nodeAuthor?.nickname ?: "联系人"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "正在回复 $name 的评论",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消回复",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { selectedReplyNode = null }
                            )
                        }
                    }
                }

                // ── 3. 底栏固定输入面板 ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 文本评论输入框
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    text = if (selectedReplyNode != null) "在此输入你的回复..." else "发表你的精彩评论...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        // 发送按钮
                        IconButton(
                            onClick = {
                                val replyText = textInput.trim()
                                if (replyText.isBlank()) return@IconButton

                                isLoadingReplies = true
                                textInput = ""

                                val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
                                val parentId = selectedReplyNode?.id ?: rootMoment.id
                                val replyToUser = if (selectedReplyNode != null) {
                                    val targetAuthor = repository.getProfile(selectedReplyNode!!.authorId)
                                    targetAuthor?.nickname ?: selectedReplyNode!!.authorId
                                } else {
                                    val targetAuthor = repository.getProfile(rootMoment.authorId)
                                    targetAuthor?.nickname ?: rootMoment.authorId
                                }

                                val myReply = Moment(
                                    id = UUID.randomUUID().toString(),
                                    authorId = "user",
                                    content = replyText,
                                    imagePath = null,
                                    videoPath = null,
                                    timestamp = currentVirtualTime + 1000L, // 略微延后 1s，防排序异常
                                    parentId = parentId,
                                    replyToUsername = replyToUser
                                )
                                repository.saveMoment(myReply)

                                // 刷新本地列表
                                repliesList = repository.getMoments()
                                selectedReplyNode = null

                                // 异步触发 AI 朋友圈盖楼回复
                                MomentEngine.triggerNpcRepliesAsync(context, myReply.id) {
                                    repliesList = repository.getMoments()
                                    isLoadingReplies = false
                                }
                                // Toast.makeText(context, "评论发表成功，虚拟时间已推进", Toast.LENGTH_SHORT).show()
                            },
                            enabled = textInput.trim().isNotBlank() && !isLoadingReplies,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (textInput.trim().isBlank() || isLoadingReplies) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    CircleShape
                                )
                        ) {
                            if (isLoadingReplies) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "发送",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 包装为 repository 调用的顶层重载，符合 AppScreen 路由
 */
@Composable
fun ChatMomentThreadScreen(
    momentId: String,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { MomentRepository(context) }
    ChatMomentThreadScreen(
        momentId = momentId,
        repository = repository,
        onGoBack = onGoBack,
        modifier = modifier
    )
}

/**
 * 二级页主动态大卡片
 */
@Composable
fun ThreadRootCard(
    moment: Moment,
    repository: MomentRepository,
    onDelete: (Moment) -> Unit,
    modifier: Modifier = Modifier
) {
    val author = remember(moment.authorId) { repository.getProfile(moment.authorId) }
    val authorName = author?.nickname ?: "联系人"
    val authorUsername = author?.username ?: moment.authorId
    val avatarPath = author?.avatar ?: ""

    val formattedTime = remember(moment.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
        sdf.format(Date(moment.timestamp))
    }

    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showMenu = true })
                }
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarView(avatarPath = avatarPath, name = authorName, size = 48.dp)
                Column {
                    Text(
                        text = authorName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (moment.content.isNotBlank()) {
                Text(
                    text = moment.content,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 拟真附件卡渲染
            if (moment.imagePath != null || moment.videoPath != null) {
                MomentAttachmentView(
                    imagePath = moment.imagePath,
                    videoPath = moment.videoPath,
                    timestamp = moment.timestamp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Text(
                text = "发布于: $formattedTime",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (moment.authorId == "user") {
                DropdownMenuItem(
                    text = { Text("删除动态", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete(moment)
                    }
                )
            }
        }
    }
}

/**
 * 评论回复卡片（支持嵌套缩进及长按删除、点击回复）
 */
@Composable
fun ThreadReplyItem(
    reply: Moment,
    depth: Int,
    repository: MomentRepository,
    onReplyClick: () -> Unit,
    onDelete: (Moment) -> Unit,
    modifier: Modifier = Modifier
) {
    val author = remember(reply.authorId) { repository.getProfile(reply.authorId) }
    val authorName = author?.nickname ?: "联系人"
    val authorUsername = author?.username ?: reply.authorId
    val avatarPath = author?.avatar ?: ""

    // 缩进距离，最高封顶 42dp 防止横向坍缩
    val startIndent = remember(depth) { (depth * 14).dp.coerceAtMost(42.dp) }
    val repliesCount = remember(reply.id, repository) { repository.getRepliesTo(reply.id).size }

    val formattedTime = remember(reply.timestamp) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINESE)
        sdf.format(Date(reply.timestamp))
    }

    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AvatarView(avatarPath = avatarPath, name = authorName, size = 30.dp)

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (reply.authorId == "user") {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onReplyClick() },
                            onLongPress = { showMenu = true }
                        )
                    }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 第一行：作者 + 时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = authorName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // 针对特定人回复的批注 (只有二级及以上评论 depth > 0 才显示)
                if (depth > 0 && reply.replyToUsername != null) {
                    Text(
                        text = "回复了 ${reply.replyToUsername}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                // 回复评论内容
                Text(
                    text = reply.content,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )


            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (reply.authorId == "user") {
                    DropdownMenuItem(
                        text = { Text("删除评论", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete(reply)
                        }
                    )
                }
            }
        }
    }
}
