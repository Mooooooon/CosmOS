package com.moonlib.cosmos.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.ui.chat.AvatarView

/**
 * @ 角色多选 Dialog
 */
@Composable
fun DiaryAtCharacterDialog(
    onDismissRequest: () -> Unit,
    availableCharacters: List<CharacterProfile>,
    selectedCharacterIds: List<String>,
    onSelectedCharactersChange: (List<String>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "选择参与本篇日记的角色",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (availableCharacters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无可用角色档案，请先在档案 APP 中创建角色", fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableCharacters) { char ->
                        val isSelected = selectedCharacterIds.contains(char.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    val newSelection = if (isSelected) {
                                        selectedCharacterIds.filter { it != char.id }
                                    } else {
                                        selectedCharacterIds + char.id
                                    }
                                    onSelectedCharactersChange(newSelection)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarView(
                                avatarPath = char.avatar,
                                name = char.name,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = char.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    val newSelection = if (checked == true) {
                                        selectedCharacterIds + char.id
                                    } else {
                                        selectedCharacterIds.filter { it != char.id }
                                    }
                                    onSelectedCharactersChange(newSelection)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("确定", color = Color.White)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
