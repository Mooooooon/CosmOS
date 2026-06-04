package com.moonlib.cosmos.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.chat.ChatMessage
import java.util.Locale

@Composable
internal fun TransferBubble(msg: ChatMessage) {
    val isCollected = msg.extra == "collected"
    val transferBackground = if (isCollected) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
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
            .background(transferBackground)
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
                    .background(
                        MaterialTheme.colorScheme.onPrimary.copy(
                            alpha = if (isCollected) 0.15f else 0.25f
                        ),
                        CircleShape
                    ),
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
