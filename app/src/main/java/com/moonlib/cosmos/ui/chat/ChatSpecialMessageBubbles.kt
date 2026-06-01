package com.moonlib.cosmos.ui.chat

// import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moonlib.cosmos.data.chat.ChatMessage
import java.util.Locale

/**
 * 特殊消息气泡分发与渲染组件
 *
 * 职责单一：根据消息类型，绘制极其精致、拟真和高质感的特殊消息气泡（图片、视频、红包、转账、位置），并处理点击交互状态。
 */
@Composable
fun SpecialMessageBubble(
    msg: ChatMessage,
    isUser: Boolean,
    contactName: String,
    contactCharacterId: String = "",
    onUpdateMessage: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRedPacketDialog by remember { mutableStateOf(false) }
    var showMediaPreview by remember { mutableStateOf(false) }

    val handleBubbleClick = {
        when (msg.type) {
            "red_packet" -> {
                if (msg.extra != "received") {
                    showRedPacketDialog = true
                } else {
                    // Toast.makeText(context, "红包已拆开，金额已存入零钱", Toast.LENGTH_SHORT).show()
                }
            }
            "transfer" -> {
                if (msg.extra != "collected") {
                    // 模拟收钱交互
                    val updated = msg.copy(extra = "collected")
                    onUpdateMessage(updated)
                    // Toast.makeText(context, "已确认收款，金额 ￥${msg.content} 已存入钱包", Toast.LENGTH_SHORT).show()
                } else {
                    // Toast.makeText(context, "已收钱，款项已存入钱包", Toast.LENGTH_SHORT).show()
                }
            }
            "image" -> {
                showMediaPreview = true
            }
            "video" -> {
                showMediaPreview = true
            }
            "voice" -> Unit
            "location" -> {
                // Toast.makeText(context, "导航去: ${msg.content}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { handleBubbleClick() }
    ) {
        when (msg.type) {
            "image" -> ImageBubble(content = msg.content)
            "video" -> VideoBubble(content = msg.content)
            "voice" -> VoiceMessageBubble(
                msg = msg,
                isUser = isUser,
                contactCharacterId = contactCharacterId,
                onUpdateMessage = onUpdateMessage
            )
            "red_packet" -> RedPacketBubble(msg = msg, isUser = isUser)
            "transfer" -> TransferBubble(msg = msg, isUser = isUser)
            "location" -> LocationBubble(content = msg.content)
        }
    }

    if (showRedPacketDialog) {
        RedPacketOpenDialog(
            senderName = if (isUser) "自己" else contactName,
            wishText = msg.extra ?: "恭喜发财，大吉大利",
            amountText = msg.content,
            onDismiss = { showRedPacketDialog = false },
            onOpenSuccess = {
                val updated = msg.copy(extra = "received")
                onUpdateMessage(updated)
                showRedPacketDialog = false
                // Toast.makeText(context, "成功领取红包 ￥${msg.content} 元！", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showMediaPreview) {
        MediaPreviewDialog(
            type = msg.type,
            content = msg.content,
            onDismiss = { showMediaPreview = false }
        )
    }
}

// ─── 1. 图片气泡 ───────────────────────────────────────────────
@Composable
private fun ImageBubble(content: String) {
    Box(
        modifier = Modifier
            .size(180.dp, 130.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "图片",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "图片: $content",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── 2. 视频气泡 ───────────────────────────────────────────────
@Composable
private fun VideoBubble(content: String) {
    Box(
        modifier = Modifier
            .size(180.dp, 130.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                RoundedCornerShape(16.dp)
            )
    ) {
        // 播放按钮装饰在中心
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // 底部蒙层标签显示文字描述
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = content,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "00:15",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── 3. 红包气泡 ───────────────────────────────────────────────
@Composable
private fun RedPacketBubble(msg: ChatMessage, isUser: Boolean) {
    val isReceived = msg.extra == "received"
    val redBg = if (isReceived) MaterialTheme.colorScheme.error.copy(alpha = 0.55f) else MaterialTheme.colorScheme.error
    val goldColor = MaterialTheme.colorScheme.errorContainer

    Column(
        modifier = Modifier
            .width(220.dp)
            .background(redBg)
            .border(
                1.dp,
                if (isReceived) Color.Transparent else goldColor.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 红包图标或“福”字
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isReceived) goldColor.copy(alpha = 0.5f) else goldColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "褔",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = msg.extra ?: "恭喜发财，大吉大利",
                    color = MaterialTheme.colorScheme.onPrimary, // 红色底上的白字/淡字
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isReceived) "已拆开" else "查看红包",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        // 底部条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "CosmOS红包",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

// ─── 4. 转账气泡 ───────────────────────────────────────────────
@Composable
private fun TransferBubble(msg: ChatMessage, isUser: Boolean) {
    val isCollected = msg.extra == "collected"
    val transferBg = if (isCollected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.tertiaryContainer
    val formattedAmount = remember(msg.content) {
        try {
            String.format(Locale.US, "%.2f", msg.content.toDouble())
        } catch (e: Exception) {
            msg.content
        }
    }

    Column(
        modifier = Modifier
            .width(220.dp)
            .background(transferBg)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isCollected) 0.15f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCollected) Icons.Default.CheckCircle else Icons.Default.SwapHoriz,
                    contentDescription = "转账",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "￥$formattedAmount",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isCollected) "已收款" else "微信转账 (待收款)",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        // 底部条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "CosmOS转账",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

// ─── 5. 位置气泡 ───────────────────────────────────────────────
@Composable
private fun LocationBubble(content: String) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
    ) {
        // 上层：地名与图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "定位",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 下层：抽象科幻地图背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 绘制网格道路线
                val midX = size.width / 2
                val midY = size.height / 2
                
                // 纵横主道路
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 6.dp.toPx()
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(midX, 0f),
                    end = Offset(midX, size.height),
                    strokeWidth = 6.dp.toPx()
                )
                
                // 环路
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.15f),
                    center = Offset(midX, midY),
                    radius = 24.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )

                // 细网格支路
                drawLine(
                    color = Color.Gray.copy(alpha = 0.1f),
                    start = Offset(midX - 50.dp.toPx(), 0f),
                    end = Offset(midX - 50.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.1f),
                    start = Offset(midX + 50.dp.toPx(), 0f),
                    end = Offset(midX + 50.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 中心定位大标
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "我的位置",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ─── 6. 沉浸式红包领取仪式 Dialog ──────────────────────────────────────
@Composable
fun RedPacketOpenDialog(
    senderName: String,
    wishText: String,
    amountText: String,
    onDismiss: () -> Unit,
    onOpenSuccess: () -> Unit
) {
    var isOpened by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .width(280.dp)
                .height(400.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 上半部分流线微拱形装饰
                val errorBgColor = MaterialTheme.colorScheme.error
                val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(errorBgColor, onErrorContainerColor)
                        ),
                        size = size
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 头部信息
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            text = senderName,
                            color = MaterialTheme.colorScheme.errorContainer,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "发给你一个红包",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = wishText,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isOpened) {
                        // 未开启：展示金币大“開”按钮
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(4.dp, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), CircleShape)
                                .clickable {
                                    isOpened = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "開",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp
                            )
                        }

                        // 底部关闭
                        TextButton(onClick = onDismiss) {
                            Text("关闭", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    } else {
                        // 已开启：展示金额和领取状态
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "￥$amountText",
                                color = MaterialTheme.colorScheme.errorContainer,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "已存入CosmOS钱包零钱",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        // 确定关闭
                        Button(
                            onClick = onOpenSuccess,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("放入钱包", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 像素级多媒体（图片/视频）全屏放大查看 Dialog 浮层，100% 宽度纯色卡片，文字水平、垂直双向绝对居中，无图标
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewDialog(
    type: String,
    content: String,
    onDismiss: () -> Unit
) {
    val cardBgColor = MaterialTheme.colorScheme.primaryContainer
    val cardTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false // 彻底拉满全屏，没有任何白边留空！
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim) // 使用系统遮罩底色
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 中央媒体高保真 100% 宽度长方形大框，背景为纯色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f) // 完美的超宽屏 100% 宽度比例
                    .background(cardBgColor)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center // 水平、垂直双向绝对居中
            ) {
                Text(
                    text = content,
                    color = cardTextColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center, // 文字水平居中
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
