package com.moonlib.cosmos.ui.time

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private data class QuickTimeOption(
    val label: String,
    val deltaMillis: Long
)

/**
 * 目标时间选择器。
 *
 * 职责单一：负责精确选择目标时间，以及从当前虚拟时间快速选择常用未来目标。
 */
@Composable
fun TimeTargetPicker(
    currentTimeMillis: Long,
    targetTimeMillis: Long,
    onTargetTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val targetText = remember(targetTimeMillis) {
        formatDateTime(targetTimeMillis, "yyyy年MM月dd日 HH:mm")
    }
    val directionText = remember(currentTimeMillis, targetTimeMillis) {
        val deltaMillis = targetTimeMillis - currentTimeMillis
        when {
            deltaMillis > 0L -> "将前进 ${formatDuration(deltaMillis)}"
            deltaMillis < 0L -> "将回退 ${formatDuration(-deltaMillis)}"
            else -> "目标时间与当前时间一致"
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "选择目标时间",
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "目标时间",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = targetText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = directionText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showDatePicker(
                                context = context,
                                initialTimeMillis = targetTimeMillis,
                                onTimeSelected = onTargetTimeChange
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "选择日期",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "日期", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            showTimePicker(
                                context = context,
                                initialTimeMillis = targetTimeMillis,
                                onTimeSelected = onTargetTimeChange
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "选择时间",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "时间", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "常用跳转",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    quickTimeRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { option ->
                                QuickTimeChip(
                                    label = option.label,
                                    onClick = {
                                        onTargetTimeChange(currentTimeMillis + option.deltaMillis)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTimeChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val quickTimeRows = listOf(
    listOf(
        QuickTimeOption("+10分钟", 10 * 60 * 1000L),
        QuickTimeOption("+30分钟", 30 * 60 * 1000L),
        QuickTimeOption("+1小时", 60 * 60 * 1000L),
        QuickTimeOption("+2小时", 2 * 60 * 60 * 1000L)
    ),
    listOf(
        QuickTimeOption("+4小时", 4 * 60 * 60 * 1000L),
        QuickTimeOption("+6小时", 6 * 60 * 60 * 1000L),
        QuickTimeOption("+8小时", 8 * 60 * 60 * 1000L),
        QuickTimeOption("+12小时", 12 * 60 * 60 * 1000L)
    ),
    listOf(
        QuickTimeOption("+1天", 24 * 60 * 60 * 1000L),
        QuickTimeOption("+2天", 2 * 24 * 60 * 60 * 1000L),
        QuickTimeOption("+3天", 3 * 24 * 60 * 60 * 1000L),
        QuickTimeOption("+7天", 7 * 24 * 60 * 60 * 1000L)
    )
)

private fun showDatePicker(
    context: Context,
    initialTimeMillis: Long,
    onTimeSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = initialTimeMillis
    }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val updated = Calendar.getInstance().apply {
                timeInMillis = initialTimeMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onTimeSelected(updated.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun showTimePicker(
    context: Context,
    initialTimeMillis: Long,
    onTimeSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = initialTimeMillis
    }
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val updated = Calendar.getInstance().apply {
                timeInMillis = initialTimeMillis
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onTimeSelected(updated.timeInMillis)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    ).show()
}

private fun formatDateTime(timeMillis: Long, pattern: String): String {
    val ldt = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timeMillis),
        ZoneId.systemDefault()
    )
    return ldt.format(DateTimeFormatter.ofPattern(pattern))
}

private fun formatDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / (60 * 1000L)
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes % (24 * 60) / 60
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("${days}天")
        if (hours > 0) add("${hours}小时")
        if (minutes > 0 || isEmpty()) add("${minutes}分钟")
    }.joinToString("")
}
