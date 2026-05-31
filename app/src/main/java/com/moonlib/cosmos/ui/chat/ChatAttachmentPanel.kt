package com.moonlib.cosmos.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 附件面板类型
 */
enum class AttachmentType(
    val title: String,
    val icon: ImageVector,
    val typeName: String
) {
    IMAGE("图片", Icons.Default.Image, "image"),
    VIDEO("视频", Icons.Default.Videocam, "video"),
    RED_PACKET("红包", Icons.Default.Redeem, "red_packet"),
    TRANSFER("转账", Icons.Default.SwapHoriz, "transfer"),
    LOCATION("位置", Icons.Default.LocationOn, "location")
}

/**
 * 语义化获取附件图标对应的主题色
 */
@Composable
fun AttachmentType.getColor(): Color {
    return when (this) {
        AttachmentType.IMAGE -> MaterialTheme.colorScheme.primary
        AttachmentType.VIDEO -> MaterialTheme.colorScheme.secondary
        AttachmentType.RED_PACKET -> MaterialTheme.colorScheme.error
        AttachmentType.TRANSFER -> MaterialTheme.colorScheme.tertiary
        AttachmentType.LOCATION -> MaterialTheme.colorScheme.primary
    }
}

/**
 * 聊天输入附件面板组件
 *
 * 职责单一：负责展示点击输入框右侧加号后弹出的功能面板，支持 5 种类型的多媒体/交互模拟发送。
 */
@Composable
fun ChatAttachmentPanel(
    onSelect: (AttachmentType) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            AttachmentType.IMAGE,
            AttachmentType.VIDEO,
            AttachmentType.RED_PACKET,
            AttachmentType.TRANSFER,
            AttachmentType.LOCATION
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 第一排图标：渲染前 4 个图标以确保等宽对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..3) {
                    if (i < items.size) {
                        val item = items[i]
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AttachmentItem(item = item, onClick = { onSelect(item) })
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 第二排图标：渲染剩余图标，未填满处使用空等比权重占位，保证上下两行绝对像素级对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 4..7) {
                    if (i < items.size) {
                        val item = items[i]
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AttachmentItem(item = item, onClick = { onSelect(item) })
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentItem(
    item: AttachmentType,
    onClick: () -> Unit
) {
    val iconColor = item.getColor()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── 1. 图片输入 Dialog ─────────────────────────────────────────
@Composable
fun ImageAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发送图片", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "发送一张图片。请输入图片的描述文字（例如：可爱的小猫、今天的晚餐）：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("例如：可爱的小猫") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val final = text.trim()
                    onConfirm(if (final.isEmpty()) "一张图片" else final)
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 2. 视频输入 Dialog ─────────────────────────────────────────
@Composable
fun VideoAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发送视频", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "发送一段视频。请输入视频的描述文字（例如：海边日落、猫咪打滚）：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("例如：海边日落") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val final = text.trim()
                    onConfirm(if (final.isEmpty()) "一段短视频" else final)
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 3. 红包输入 Dialog ─────────────────────────────────────────
@Composable
fun RedPacketAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: String, wish: String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var wish by remember { mutableStateOf("恭喜发财，大吉大利") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发红包", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "请输入红包的金额和留言：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMsg = ""
                    },
                    label = { Text("金额 (元)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = wish,
                    onValueChange = { wish = it },
                    label = { Text("留言") },
                    placeholder = { Text("恭喜发财，大吉大利") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amtDouble = amount.toDoubleOrNull()
                    if (amtDouble == null || amtDouble <= 0) {
                        errorMsg = "请输入大于 0 的有效金额"
                    } else if (amtDouble > 200.0) {
                        errorMsg = "单次红包金额不能超过 200.00 元"
                    } else {
                        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amtDouble)
                        val finalWish = wish.trim().ifEmpty { "恭喜发财，大吉大利" }
                        onConfirm(formattedAmount, finalWish)
                    }
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("塞钱进红包")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 4. 转账输入 Dialog ─────────────────────────────────────────
@Composable
fun TransferAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转账", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "请输入转账金额：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMsg = ""
                    },
                    label = { Text("转账金额 (元)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amtDouble = amount.toDoubleOrNull()
                    if (amtDouble == null || amtDouble <= 0) {
                        errorMsg = "请输入大于 0 的有效金额"
                    } else {
                        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amtDouble)
                        onConfirm(formattedAmount)
                    }
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("转账")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 5. 位置输入 Dialog ─────────────────────────────────────────
@Composable
fun LocationAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发送位置", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "发送你的当前地理位置。请输入位置的名称（例如：猫咪咖啡馆、市中心图书馆）：",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("例如：猫咪咖啡馆") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val final = text.trim()
                    onConfirm(if (final.isEmpty()) "我的位置" else final)
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
