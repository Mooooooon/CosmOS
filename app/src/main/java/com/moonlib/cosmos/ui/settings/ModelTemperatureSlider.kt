package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 模型温度滑块。
 *
 * 职责单一：展示并归一化 0.0 到 2.0、步进 0.1 的温度设置。
 */
@Composable
fun ModelTemperatureSlider(
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedTemperature = normalizeTemperature(temperature)

    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "创意温度 (Temperature)",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            Text(
                text = String.format("%.1f", normalizedTemperature),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = normalizedTemperature,
            onValueChange = { onTemperatureChange(normalizeTemperature(it)) },
            valueRange = 0.0f..2.0f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("精确 (0.0)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 10.sp)
            Text("默认 (1.0)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 10.sp)
            Text("发散 (2.0)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}

fun normalizeTemperature(value: Float): Float {
    return (value.coerceIn(0.0f, 2.0f) * 10).roundToInt() / 10f
}
