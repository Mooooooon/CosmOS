package com.moonlib.cosmos.ui.time

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.time.TimeSkipEngine
import com.moonlib.cosmos.data.time.VirtualTimeManager
import kotlinx.coroutines.launch

/**
 * 时间跳转执行面板。
 *
 * 职责单一：根据目标时间和推演开关，执行直接跳转或统一时间推演。
 */
@Composable
fun TimeTravelControlCard(
    currentTimeMillis: Long,
    targetTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val canSimulate = targetTimeMillis > currentTimeMillis

    var shouldSimulate by remember { mutableStateOf(false) }
    var userActivity by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var resultIsError by remember { mutableStateOf(false) }

    LaunchedEffect(canSimulate) {
        if (!canSimulate) {
            shouldSimulate = false
            userActivity = ""
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "跳转设置",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (canSimulate) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            }
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "需要推演",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (canSimulate) {
                                    "推演这段时间内的私聊、朋友圈和推特动态"
                                } else {
                                    "向后调整不会触发推演"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = shouldSimulate,
                        onCheckedChange = { 
                            shouldSimulate = it 
                            if (!it) userActivity = ""
                        },
                        enabled = canSimulate && !isRunning
                    )
                }

                if (shouldSimulate) {
                    OutlinedTextField(
                        value = userActivity,
                        onValueChange = { userActivity = it },
                        placeholder = {
                            Text(
                                text = "写下你在这段时间内的行动描述...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        label = {
                            Text(
                                text = "行动备注",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        resultText = null
                        resultIsError = false
                        if (!shouldSimulate) {
                            VirtualTimeManager.rollbackTime(targetTimeMillis)
                            resultText = if (targetTimeMillis <= currentTimeMillis) {
                                "时间已设置，未触发推演。"
                            } else {
                                "时间已跳转，未触发推演。"
                            }
                            return@Button
                        }

                        isRunning = true
                        coroutineScope.launch {
                            val result = TimeSkipEngine.executeTimeSkip(
                                context = context,
                                startTimeMillis = currentTimeMillis,
                                endTimeMillis = targetTimeMillis,
                                userActivity = userActivity
                            )
                            isRunning = false
                            resultIsError = !result.success
                            resultText = if (result.success) {
                                "推演完成：收到 ${result.simulatedMessageCount} 条私聊，新增 ${result.simulatedMomentCount} 条朋友圈动态，${result.simulatedTweetCount} 条推特动态。"
                            } else {
                                "推演失败：${result.errorMessage}"
                            }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (shouldSimulate) "跳转并推演" else "确定跳转",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                    )
                }

                resultText?.let { text ->
                    ResultMessage(
                        text = text,
                        isError = resultIsError
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultMessage(
    text: String,
    isError: Boolean
) {
    val color = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
