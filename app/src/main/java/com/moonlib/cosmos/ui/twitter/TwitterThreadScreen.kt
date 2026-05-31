package com.moonlib.cosmos.ui.twitter

// import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.twitter.Tweet
import com.moonlib.cosmos.data.twitter.TwitterEngine
import com.moonlib.cosmos.data.twitter.TwitterRepository
import com.moonlib.cosmos.ui.chat.AvatarView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 评论区盖楼详情页面
 * 
 * 职责单一：负责展示主推文、计算并嵌套渲染缩进回复树（套娃回复）、选定特定节点回复、实时发送评论并触发 NPC 脑洞互动回复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitterThreadScreen(
    rootTweet: Tweet,
    repository: TwitterRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var repliesList by remember { mutableStateOf(repository.getTweets()) }
    var textInput by remember { mutableStateOf("") }
    
    // 追踪当前选定的回复节点（为 null 代表回复主推特，否则代表回复某条特定的 NPC 评论）
    var selectedReplyNode by remember { mutableStateOf<Tweet?>(null) }
    var isLoadingReplies by remember { mutableStateOf(false) }

    // 递归构建缩进回复树
    val threadItems = remember(repliesList, rootTweet.id) {
        val result = mutableListOf<Pair<Tweet, Int>>() // Pair<Tweet, Depth>
        
        fun buildTree(parentId: String, depth: Int) {
            val children = repliesList.filter { it.parentId == parentId }.sortedBy { it.timestamp }
            for (child in children) {
                result.add(Pair(child, depth))
                buildTree(child.id, depth + 1) // 递归加载子节点，递增缩进深度
            }
        }
        
        buildTree(rootTweet.id, 0)
        result
    }

    // 物理返回键安全退出
    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "推特正文及盖楼",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 1. 滚动视图区（主推文 + 回复楼层列表） ─────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 主推文
                    item {
                        ThreadRootCard(
                            tweet = rootTweet,
                            repository = repository,
                            onDeleteTweet = { toDelete ->
                                repository.deleteTweet(toDelete.id)
                                onBackClick() // 主推文被删除，安全退回上一级
                                // Toast.makeText(context, "推文已删除", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 评论楼层（缩进显示）
                    if (threadItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "💬 暂无评论。来抢沙发吧！",
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
                                onReplyNodeClick = { selectedReplyNode = reply },
                                onDeleteReply = { toDelete ->
                                    repository.deleteTweet(toDelete.id)
                                    repliesList = repository.getTweets() // 刷新列表
                                    // Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // ── 2. 回复发送状态指示（如正在回复某人） ─────────────
                AnimatedVisibility(
                    visible = selectedReplyNode != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    selectedReplyNode?.let { node ->
                        val nodeAuthor = repository.getProfile(node.authorId)
                        val name = nodeAuthor?.nickname ?: "未知角色"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "正在回复 @${nodeAuthor?.username ?: node.authorId} ($name) 的评论",
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
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 回复输入框
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    text = if (selectedReplyNode != null) "在此输入回复此评论的文字..." else "发表你的精彩评论...",
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

                        // 发送评论按钮
                        IconButton(
                            onClick = {
                                val replyText = textInput.trim()
                                if (replyText.isBlank()) return@IconButton

                                isLoadingReplies = true
                                textInput = ""

                                val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
                                val parentId = selectedReplyNode?.id ?: rootTweet.id
                                val replyToUser = if (selectedReplyNode != null) {
                                    val targetAuthor = repository.getProfile(selectedReplyNode!!.authorId)
                                    targetAuthor?.username ?: selectedReplyNode!!.authorId
                                } else {
                                    val targetAuthor = repository.getProfile(rootTweet.authorId)
                                    targetAuthor?.username ?: rootTweet.authorId
                                }

                                val myReply = Tweet(
                                    id = UUID.randomUUID().toString(),
                                    authorId = "user",
                                    content = replyText,
                                    imagePath = null,
                                    timestamp = currentVirtualTime + 1000L, // 略微延后 1s，防排序异常
                                    parentId = parentId,
                                    replyToUsername = replyToUser
                                )
                                repository.saveTweet(myReply)

                                // 刷新时间线
                                repliesList = repository.getTweets()
                                selectedReplyNode = null

                                // 触发 AI 异步评论盖楼流程，支持高保真真实通讯状态回调
                                TwitterEngine.triggerNpcRepliesAsync(context, myReply.id) {
                                    repliesList = repository.getTweets()
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
                                    contentDescription = "发送评论",
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
 * 详情页主推文展示
 */
@Composable
fun ThreadRootCard(
    tweet: Tweet,
    repository: TwitterRepository,
    onDeleteTweet: (Tweet) -> Unit,
    modifier: Modifier = Modifier
) {
    val author = remember(tweet.authorId) { repository.getProfile(tweet.authorId) }
    val authorName = author?.nickname ?: "未知角色"
    val authorUsername = author?.username ?: tweet.authorId
    val avatarPath = author?.avatar ?: ""

    val formattedTime = remember(tweet.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
        sdf.format(Date(tweet.timestamp))
    }

    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showMenu = true }
                    )
                }
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarView(
                    avatarPath = avatarPath,
                    name = authorName,
                    size = 48.dp
                )
                Column {
                    Text(
                        text = authorName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "@$authorUsername",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Text(
                text = tweet.content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 渲染大图
            if (tweet.imagePath != null) {
                TweetImage(imagePath = tweet.imagePath, modifier = Modifier.padding(vertical = 4.dp))
            }

            Text(
                text = "发布于虚拟时间: $formattedTime",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("删除推文", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDeleteTweet(tweet)
                }
            )
        }
    }
}

/**
 * 评论回复卡片（支持缩进树渲染及点击盖楼与长按删除）
 */
@Composable
fun ThreadReplyItem(
    reply: Tweet,
    depth: Int,
    repository: TwitterRepository,
    onReplyNodeClick: () -> Unit,
    onDeleteReply: (Tweet) -> Unit,
    modifier: Modifier = Modifier
) {
    val author = remember(reply.authorId) { repository.getProfile(reply.authorId) }
    val authorName = author?.nickname ?: "未知角色"
    val authorUsername = author?.username ?: reply.authorId
    val avatarPath = author?.avatar ?: ""

    // 缩进距离，最高封顶 48dp 避免横向空间坍缩
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
        AvatarView(
            avatarPath = avatarPath,
            name = authorName,
            size = 30.dp
        )

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
                            onTap = { onReplyNodeClick() },
                            onLongPress = { showMenu = true }
                        )
                    }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 作者 + 目标回复人 + 时间
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
                        Text(
                            text = "@$authorUsername",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // 对特定人回复的指示
                if (reply.replyToUsername != null) {
                    Text(
                        text = "回复 @${reply.replyToUsername}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                // 评论内容
                Text(
                    text = reply.content,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 下方盖楼小指示
                if (repliesCount > 0) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↙ 叠楼 $repliesCount 层 (点击可回复)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("删除评论", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDeleteReply(reply)
                    }
                )
            }
        }
    }
}
