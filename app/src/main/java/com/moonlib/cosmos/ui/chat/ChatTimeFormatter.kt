package com.moonlib.cosmos.ui.chat

import com.moonlib.cosmos.data.time.VirtualTimeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 聊天时间显示格式化工具。
 */
internal object ChatTimeFormatter {
    fun formatSessionTime(
        timestamp: Long,
        currentTimeMillis: Long = VirtualTimeManager.getCurrentTimeMillis()
    ): String {
        val nowCalendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }
        val messageCalendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val date = Date(timestamp)

        return when {
            isSameDay(nowCalendar, messageCalendar) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            }

            isYesterday(nowCalendar, messageCalendar) -> {
                "昨天"
            }

            else -> {
                SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
            }
        }
    }

    fun formatTimelineTime(
        timestamp: Long,
        currentTimeMillis: Long = VirtualTimeManager.getCurrentTimeMillis()
    ): String {
        val nowCalendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }
        val messageCalendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val date = Date(timestamp)
        val pattern = if (isSameDay(nowCalendar, messageCalendar)) {
            "HH:mm"
        } else {
            "MM-dd HH:mm"
        }

        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, message: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(yesterday, message)
    }
}
