package com.moonlib.cosmos.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.profile.CharacterProfile

/**
 * AI 生成人设时的已有角色参考选择器。
 *
 * 职责单一：展示可作为参考的既有角色，并维护外部传入的多选结果。
 */
@Composable
fun AiReferenceProfileSelector(
    profiles: List<CharacterProfile>,
    selectedProfileIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (profiles.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "参考已有角色",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            text = "可多选。生成朋友、亲人或同世界观角色时，会连同用户人设一起提供给 AI 参考。",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        profiles.forEach { profile ->
            val selected = profile.id in selectedProfileIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { checked ->
                        val nextSelection = selectedProfileIds.toMutableSet()
                        if (checked) {
                            nextSelection.add(profile.id)
                        } else {
                            nextSelection.remove(profile.id)
                        }
                        onSelectionChange(nextSelection)
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name.ifBlank { "未命名角色" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (profile.keywords.isNotEmpty()) {
                        Text(
                            text = profile.keywords.joinToString(" / "),
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}
