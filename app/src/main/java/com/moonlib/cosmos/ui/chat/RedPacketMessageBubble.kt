package com.moonlib.cosmos.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.redPacketState

@Composable
internal fun RedPacketBubble(msg: ChatMessage) {
    val state = msg.redPacketState()
    val redBackground = if (state.isReceived) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.error
    }
    val goldColor = MaterialTheme.colorScheme.errorContainer

    Column(
        modifier = Modifier
            .width(220.dp)
            .background(redBackground)
            .border(
                1.dp,
                if (state.isReceived) Color.Transparent else goldColor.copy(alpha = 0.4f),
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
                    .background(
                        if (state.isReceived) goldColor.copy(alpha = 0.5f) else goldColor,
                        CircleShape
                    ),
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
                    text = state.wish,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (state.isReceived) "已拆开" else "查看红包",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

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

@Composable
internal fun RedPacketOpenDialog(
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
                val errorBackground = MaterialTheme.colorScheme.error
                val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(errorBackground, onErrorContainer)
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
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(
                                    4.dp,
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                    CircleShape
                                )
                                .clickable { isOpened = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "開",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "关闭",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    } else {
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

                        Button(
                            onClick = onOpenSuccess,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "放入钱包",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
