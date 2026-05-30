package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiServiceType

/**
 * 服务商类型选择器。
 *
 * 职责单一：以每行三个服务商的网格展示服务类型，并回传选择结果。
 */
@Composable
fun ServiceTypeSelector(
    selectedType: AiServiceType,
    onTypeSelected: (AiServiceType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "选择服务商类型",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        AiServiceType.entries.chunked(3).forEach { rowTypes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTypes.forEach { type ->
                    ServiceTypeCard(
                        type = type,
                        isSelected = selectedType == type,
                        modifier = Modifier.weight(1f),
                        onClick = { onTypeSelected(type) }
                    )
                }
                repeat(3 - rowTypes.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ServiceTypeCard(
    type: AiServiceType,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val brandColor = serviceTypeColor(type)
    Card(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        border = if (isSelected) {
            BorderStroke(1.5.dp, brandColor)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                brandColor.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = type.displayName,
                color = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun serviceTypeColor(type: AiServiceType): Color {
    return when (type) {
        AiServiceType.OPEN_AI -> Color(0xFF10B981)
        AiServiceType.DEEP_SEEK -> Color(0xFF3B82F6)
        AiServiceType.GEMINI -> Color(0xFF8B5CF6)
        AiServiceType.VERTEX -> Color(0xFFEA4335)
    }
}
