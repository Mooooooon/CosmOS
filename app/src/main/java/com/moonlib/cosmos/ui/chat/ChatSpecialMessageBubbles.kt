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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.redPacketWish

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
    isReceived: Boolean,
    onClaimMessage: (ChatMessage) -> Unit,
    onUpdateMessage: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRedPacketDialog by remember { mutableStateOf(false) }
    var showMediaPreview by remember { mutableStateOf(false) }

    val handleBubbleClick = {
        when (msg.type) {
            "red_packet" -> {
                if (!isUser && !isReceived) {
                    showRedPacketDialog = true
                }
            }
            "transfer" -> {
                if (!isUser && !isReceived) {
                    onClaimMessage(msg)
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
            "red_packet" -> RedPacketBubble(msg = msg, isReceived = isReceived)
            "transfer" -> TransferBubble(msg = msg, isReceived = isReceived)
            "transfer_receipt" -> TransferBubble(msg = msg, isReceived = true)
            "location" -> LocationBubble(content = msg.content)
        }
    }

    if (showRedPacketDialog) {
        RedPacketOpenDialog(
            senderName = if (isUser) "自己" else contactName,
            wishText = msg.redPacketWish(),
            amountText = msg.content,
            onDismiss = { showRedPacketDialog = false },
            onOpenSuccess = {
                onClaimMessage(msg)
                showRedPacketDialog = false
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

// ─── 3. 位置气泡 ───────────────────────────────────────────────
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
