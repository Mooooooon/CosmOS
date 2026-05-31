package com.moonlib.cosmos.ui.twitter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moonlib.cosmos.data.time.VirtualTimeManager
import com.moonlib.cosmos.data.twitter.Tweet
import com.moonlib.cosmos.data.twitter.TwitterEngine
import com.moonlib.cosmos.data.twitter.TwitterRepository
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * 悬浮写推特弹窗组件
 * 
 * 职责单一：负责输入推文正文、添加配图描述、展现字数超标指示，以及确认发布触发 NPC 脑洞评论流程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitterNewTweetDialog(
    repository: TwitterRepository,
    onDismiss: () -> Unit,
    onPublishSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf("") }
    var simulatedPhotoDesc by remember { mutableStateOf<String?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }

    val characterLimit = 140
    val textLength = textInput.length

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分享新鲜事...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { if (!isPublishing) onDismiss() },
                    enabled = !isPublishing,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 多行写推框
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { if (it.length <= characterLimit) textInput = it },
                    enabled = !isPublishing,
                    placeholder = {
                        Text(
                            text = "今天有什么好玩的？发条推特吧...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    minLines = 4,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 字数微型计数与添加图片 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 添加图片动作按钮
                    IconButton(
                        onClick = { showPhotoDialog = true },
                        enabled = !isPublishing,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isPublishing) MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "添加图片",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 字数计数
                    Text(
                        text = "$textLength / $characterLimit 字",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (textLength >= characterLimit) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                }

                // 精致已选配图缩略图预览（支持删除）
                simulatedPhotoDesc?.let { desc ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "已选配图",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "已添加图片",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "“ $desc ”",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontStyle = FontStyle.Italic,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // 右上角的删除按钮
                        IconButton(
                            onClick = { simulatedPhotoDesc = null },
                            enabled = !isPublishing,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除图片",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val contentText = textInput.trim()
                    if (contentText.isBlank() && simulatedPhotoDesc == null) return@Button

                    scope.launch {
                        isPublishing = true
                        try {
                            val currentVirtualTime = VirtualTimeManager.getCurrentTimeMillis()
                            val newTweetUuid = UUID.randomUUID().toString()

                            val tweet = Tweet(
                                id = newTweetUuid,
                                authorId = "user",
                                content = contentText,
                                imagePath = simulatedPhotoDesc?.let { "simulated_image:$it" },
                                timestamp = currentVirtualTime,
                                parentId = null
                            )
                            
                            repository.saveTweet(tweet)

                            // 同步触发已关注 NPC 盖楼讨论，等待生成完毕
                            TwitterEngine.checkAndGenerateNpcReplies(context, newTweetUuid)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isPublishing = false
                            onPublishSuccess()
                        }
                    }
                },
                enabled = (textInput.trim().isNotBlank() || simulatedPhotoDesc != null) && !isPublishing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("发送中...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("发布推特", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    )

    if (showPhotoDialog) {
        AttachmentDialog(
            title = "添加照片",
            label = "请输入照片的画面描述：",
            placeholder = "例如：天台拍摄的星空与霓虹灯交错",
            onDismiss = { showPhotoDialog = false },
            onConfirm = { desc ->
                simulatedPhotoDesc = desc
                showPhotoDialog = false
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
