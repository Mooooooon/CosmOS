package com.moonlib.cosmos.ui.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 日记人称与状态卡开关配置 Dialog
 */
@Composable
fun DiarySettingsDialog(
    onDismissRequest: () -> Unit,
    perspective: String,
    onPerspectiveChange: (String) -> Unit,
    isStatusCardEnabled: Boolean,
    onStatusCardEnabledChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "日记设置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 人称选择
                Column {
                    Text(
                        text = "叙事人称",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = perspective == "first",
                            onClick = { onPerspectiveChange("first") }
                        )
                        Text(text = "第一人称 (以“我”视角创作)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = perspective == "third",
                            onClick = { onPerspectiveChange("third") }
                        )
                        Text(text = "第三人称 (上帝/旁观视角创作)", fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // 状态卡开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "日记状态卡",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "开启后，AI 将在生成日记时根据剧情更新并展示所有人的状态卡。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = isStatusCardEnabled,
                        onCheckedChange = onStatusCardEnabledChange
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("完成", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
