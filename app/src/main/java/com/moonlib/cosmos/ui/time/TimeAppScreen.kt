package com.moonlib.cosmos.ui.time

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.time.VirtualTimeManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * 虚拟时间控制 App 主页面
 *
 * 职责单一：渲染虚拟时间的详细数字时钟状态，以及修改虚拟时间（微调与精确设定）的各个功能面板组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 拦截物理/手势返回按键
    BackHandler(enabled = true) {
        onGoBack()
    }

    // 订阅全局虚拟时间流，一旦修改会立即在此响应刷新
    val currentVirtualTime by VirtualTimeManager.currentTimeFlow.collectAsState()

    // 格式化当前虚拟时间属性，防止每次重新组合时都多次处理转换
    val timeInfo = remember(currentVirtualTime) {
        val instant = Instant.ofEpochMilli(currentVirtualTime)
        val ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val dateStr = ldt.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
        val weekStr = ldt.format(DateTimeFormatter.ofPattern("EEEE"))
        Triple(timeStr, dateStr, weekStr)
    }

    val (timeStr, dateStr, weekStr) = timeInfo

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "虚拟时间控制",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 1. 时钟面板（大屏卡片展示） ──
            TimeClockPanel(
                timeStr = timeStr,
                dateStr = dateStr,
                weekStr = weekStr
            )

            // ── 2. 快捷时间微调控制 ──
            TimeControlsCard(
                currentTimeMillis = currentVirtualTime,
                onTimeChange = { newTime ->
                    VirtualTimeManager.rollbackTime(newTime)
                }
            )

            // ── 3. 系统动作（校准与精确设定） ──
            TimeActionCard(
                currentTimeMillis = currentVirtualTime,
                onTimeChange = { newTime ->
                    VirtualTimeManager.rollbackTime(newTime)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 虚拟时钟显示面板，大卡片展示当前的虚拟时钟状态
 */
@Composable
private fun TimeClockPanel(
    timeStr: String,
    dateStr: String,
    weekStr: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // “沙盒时间”标签
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFF7C3AED).copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0xFF7C3AED))
                    )
                    Text(
                        text = "VIRTUAL SANDBOX TIME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 时间大字号数字显示 HH:mm:ss
            Text(
                text = timeStr,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 日期 & 星期
            Text(
                text = "$dateStr  $weekStr",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 快捷时间微调控制面板
 */
@Composable
private fun TimeControlsCard(
    currentTimeMillis: Long,
    onTimeChange: (Long) -> Unit
) {
    Column {
        Text(
            text = "快捷时间微调",
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
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 第一行：天数微调
                AdjustmentRow(
                    label = "日期调整",
                    minusLabel = "-1 天",
                    plusLabel = "+1 天",
                    onMinusClick = { onTimeChange(currentTimeMillis - 24 * 3600 * 1000L) },
                    onPlusClick = { onTimeChange(currentTimeMillis + 24 * 3600 * 1000L) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp)

                // 第二行：小时微调
                AdjustmentRow(
                    label = "小时调整",
                    minusLabel = "-1 小时",
                    plusLabel = "+1 小时",
                    onMinusClick = { onTimeChange(currentTimeMillis - 3600 * 1000L) },
                    onPlusClick = { onTimeChange(currentTimeMillis + 3600 * 1000L) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp)

                // 第三行：分钟微调
                AdjustmentRow(
                    label = "分钟调整",
                    minusLabel = "-10 分钟",
                    plusLabel = "+10 分钟",
                    onMinusClick = { onTimeChange(currentTimeMillis - 10 * 60 * 1000L) },
                    onPlusClick = { onTimeChange(currentTimeMillis + 10 * 60 * 1000L) }
                )
            }
        }
    }
}

/**
 * 每一行微调按钮的封装组件
 */
@Composable
private fun AdjustmentRow(
    label: String,
    minusLabel: String,
    plusLabel: String,
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onMinusClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = minusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = onPlusClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = plusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 系统动作（校准与精确设定）面板
 */
@Composable
private fun TimeActionCard(
    currentTimeMillis: Long,
    onTimeChange: (Long) -> Unit
) {
    val context = LocalContext.current

    // 精确日期与时间设定的触发函数
    val showDateTimePicker = {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }

        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timePickerDialog = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val newCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onTimeChange(newCal.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                )
                timePickerDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    Column {
        Text(
            text = "高级时间校准",
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
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 精确设定按钮
                OutlinedButton(
                    onClick = { showDateTimePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "精准设置",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "精准设定时间 (年-月-日 时:分)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                // 同步系统时间按钮
                Button(
                    onClick = { onTimeChange(System.currentTimeMillis()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "同步时间",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "同步回系统当前真实时间", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
