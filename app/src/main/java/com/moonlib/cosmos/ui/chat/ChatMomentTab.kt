package com.moonlib.cosmos.ui.chat

// import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moonlib.cosmos.data.chat.Moment
import com.moonlib.cosmos.data.chat.MomentEngine
import com.moonlib.cosmos.data.chat.MomentRepository
import com.moonlib.cosmos.data.time.VirtualTimeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * 聊天 App 内朋友圈动态 Tab 页面
 * 
 * 职责单一：渲染精美的朋友圈动态列表（仅限根动态）、控制顶部展开式发布面板及拟真附件输入弹窗，管理点赞与删除逻辑。
 */
@Composable
fun ChatMomentTab(
    onNavigateTo: (ChatNavigation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { MomentRepository(context) }

    // ── 状态管理 ───────────────────────────────────────────────
    var momentList by remember { mutableStateOf(repository.getMoments()) }

    // 每次进入动态 Tab，自动将玩家的聊天 Profile 昵称和头像同步到动态 Profile 中
    LaunchedEffect(Unit) {
        repository.syncUserProfile()
        momentList = repository.getMoments()
    }

    val refreshMoments = {
        momentList = repository.getMoments()
    }

    val rootMoments = remember(momentList) {
        momentList.filter { it.parentId == null }.sortedByDescending { it.timestamp }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部发布面板与动态列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部输入卡片项
            item {
                MomentPublishCard(
                    repository = repository,
                    onPublishSuccess = {
                        refreshMoments()
                        // Toast.makeText(context, "动态发布成功，虚拟时间已推进", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // 动态列表
            if (rootMoments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FilterVintage,
                                contentDescription = "空动态",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "✨ 暂无好友动态",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "在上方写点什么，分享你今天的想法吧",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            } else {
                items(rootMoments, key = { it.id }) { moment ->
                    MomentCard(
                        moment = moment,
                        repository = repository,
                        onClick = { onNavigateTo(ChatNavigation.MomentThread(moment.id)) },
                        onDeleteMoment = { toDelete ->
                            repository.deleteMoment(toDelete.id)
                            refreshMoments()
                            // Toast.makeText(context, "动态已成功删除", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 展开式发布动态面板
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MomentPublishCard(
    repository: MomentRepository,
    onPublishSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }

    // 模拟附件状态
    var simulatedPhotoDesc by remember { mutableStateOf<String?>(null) }
    var simulatedVideoDesc by remember { mutableStateOf<String?>(null) }

    // 录入 Dialog 触发器
    var showPhotoDialog by remember { mutableStateOf(false) }
    var showVideoDialog by remember { mutableStateOf(false) }

    val myProfile = remember { repository.getProfile("user") }
    val avatarPath = myProfile?.avatar ?: ""
    val nickname = myProfile?.nickname ?: "我"

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (!isExpanded) {
                // 收起状态：极简一长条快捷点击输入框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isExpanded = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(avatarPath = avatarPath, name = nickname, size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "分享此刻的想法...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "写动态",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // 展开状态：完整高度发布卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(avatarPath = avatarPath, name = nickname, size = 42.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = nickname,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "发表动态...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 输入区域
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    enabled = !isPublishing,
                    placeholder = {
                        Text(
                            text = "今天有什么好玩的？发条动态吧...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 渲染已选择的模拟图片缩略卡
                simulatedPhotoDesc?.let { desc ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("照片附件", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(desc, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontStyle = FontStyle.Italic, maxLines = 1)
                            }
                            IconButton(
                                onClick = { simulatedPhotoDesc = null },
                                enabled = !isPublishing
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // 渲染已选择的模拟视频缩略卡
                simulatedVideoDesc?.let { desc ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("视频附件", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(desc, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontStyle = FontStyle.Italic, maxLines = 1)
                            }
                            IconButton(
                                onClick = { simulatedVideoDesc = null },
                                enabled = !isPublishing
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 操作按钮条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：多媒体添加组
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showPhotoDialog = true },
                            enabled = !isPublishing,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.background(
                                if (isPublishing) MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("照片", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { showVideoDialog = true },
                            enabled = !isPublishing,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.background(
                                if (isPublishing) MaterialTheme.colorScheme.secondary.copy(alpha = 0.03f)
                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                            )
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("视频", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 右侧：取消 + 发布
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                isExpanded = false
                                textInput = ""
                                simulatedPhotoDesc = null
                                simulatedVideoDesc = null
                            },
                            enabled = !isPublishing
                        ) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                        }

                        val canPublish = textInput.trim().isNotBlank() || simulatedPhotoDesc != null || simulatedVideoDesc != null
                        Button(
                            onClick = {
                                scope.launch {
                                    isPublishing = true
                                    try {
                                        val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
                                        val momentId = UUID.randomUUID().toString()

                                        val photoVal = simulatedPhotoDesc?.let { "simulated_image:$it" }
                                        val videoVal = simulatedVideoDesc?.let { "simulated_video:$it" }

                                        val moment = Moment(
                                            id = momentId,
                                            authorId = "user",
                                            content = textInput.trim(),
                                            imagePath = photoVal,
                                            videoPath = videoVal,
                                            timestamp = currentVirtualTime,
                                            parentId = null
                                        )

                                        repository.saveMoment(moment)

                                        // 同步触发 NPC 脑洞大开地自动盖楼回复，等待生成完毕
                                        MomentEngine.checkAndGenerateNpcReplies(context, momentId)

                                        // 重置状态
                                        textInput = ""
                                        simulatedPhotoDesc = null
                                        simulatedVideoDesc = null
                                        isExpanded = false
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isPublishing = false
                                        onPublishSuccess()
                                    }
                                }
                            },
                            enabled = canPublish && !isPublishing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isPublishing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("发送中...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("发布", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 附件弹窗组件 ──────────────────────────────────────────
    if (showPhotoDialog) {
        AttachmentDialog(
            title = "添加照片",
            label = "选择一张照片。请输入画面描述：",
            placeholder = "例如：天台拍摄的星空与霓虹灯交错",
            onDismiss = { showPhotoDialog = false },
            onConfirm = { desc ->
                simulatedPhotoDesc = desc
                showPhotoDialog = false
            }
        )
    }

    if (showVideoDialog) {
        AttachmentDialog(
            title = "添加视频",
            label = "选择一段短视频。请输入画面描述：",
            placeholder = "例如：猫咪在地毯上追着光点扑腾的可爱特写",
            onDismiss = { showVideoDialog = false },
            onConfirm = { desc ->
                simulatedVideoDesc = desc
                showVideoDialog = false
            }
        )
    }
}

/**
 * 附件描述录入 Dialog (高保真通用)
 */
@Composable
fun AttachmentDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val final = text.trim()
                            onConfirm(final.ifEmpty { "拟真附件描述" })
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("确定", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 朋友圈动态卡片组件
 */
@Composable
fun MomentCard(
    moment: Moment,
    repository: MomentRepository,
    onClick: () -> Unit,
    onDeleteMoment: (Moment) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val author = remember(moment.authorId) { repository.getProfile(moment.authorId) }
    val authorName = author?.nickname ?: "联系人"
    val authorUsername = author?.username ?: moment.authorId
    val avatarPath = author?.avatar ?: ""

    val repliesCount = remember(moment.id, repository) { repository.getRepliesTo(moment.id).size }
    val formattedTime = remember(moment.timestamp) {
        val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE)
        sdf.format(Date(moment.timestamp))
    }

    var showMenu by remember { mutableStateOf(false) }

    // 点赞状态
    var isLiked by remember(moment.id) { mutableStateOf(repository.isLiked(moment.id)) }
    
    // 心形缩放动效
    var isLikingTriggered by remember { mutableStateOf(false) }
    val likeScale by animateFloatAsState(
        targetValue = if (isLikingTriggered) 1.4f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { isLikingTriggered = false },
        label = "LikeBounceAnimation"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { showMenu = true }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AvatarView(avatarPath = avatarPath, name = authorName, size = 44.dp)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 第一行：姓名 + 时间 + 操作点
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = authorName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "操作",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // 正文内容
                    if (moment.content.isNotBlank()) {
                        Text(
                            text = moment.content,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    // 附件渲染
                    if (moment.imagePath != null || moment.videoPath != null) {
                        MomentAttachmentView(
                            imagePath = moment.imagePath,
                            videoPath = moment.videoPath,
                            timestamp = moment.timestamp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 底部互动条：赞与评论
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // 1. 点赞按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isLikingTriggered = true
                                    isLiked = repository.toggleLike(moment.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "点赞",
                                tint = if (isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .scale(likeScale)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "赞",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // 2. 评论按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClick() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "评论",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (repliesCount > 0) "评论 ($repliesCount)" else "评论",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // 操作 DropdownMenu
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
                        onDeleteMoment(moment)
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("举报/屏蔽", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        showMenu = false
                        // Toast.makeText(context, "操作已记录，后台智能屏蔽中", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/**
 * 拟真多媒体卡片渲染 (照片/视频描述)
 */
@Composable
fun MomentAttachmentView(
    imagePath: String?,
    videoPath: String?,
    timestamp: Long = 0L,
    modifier: Modifier = Modifier
) {
    if (imagePath != null && imagePath.startsWith("simulated_image:")) {
        val desc = imagePath.removePrefix("simulated_image:")
        val fileName = remember(timestamp) {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            "IMG_${sdf.format(Date(if (timestamp > 0) timestamp else System.currentTimeMillis()))}.jpg"
        }
        // 渲染极其精美科技感的照片描述卡
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("图片", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "“ $desc ”",
                    color = Color.White.copy(alpha = 0.85f),
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Text(fileName, color = Color.White.copy(alpha = 0.25f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else if (videoPath != null && videoPath.startsWith("simulated_video:")) {
        val desc = videoPath.removePrefix("simulated_video:")
        val fileName = remember(timestamp) {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            "VID_${sdf.format(Date(if (timestamp > 0) timestamp else System.currentTimeMillis()))}.mp4"
        }
        // 渲染带有磨砂黑播放标志的短视频描述卡
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text("视频", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "“ $desc ”",
                        color = Color.White.copy(alpha = 0.85f),
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(fileName, color = Color.White.copy(alpha = 0.25f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
