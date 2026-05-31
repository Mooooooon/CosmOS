package com.moonlib.cosmos.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
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
 * 底部已勾选的参与人物 Chip Tag
 */
@Composable
fun InputCharacterTag(
    character: CharacterProfile,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AvatarView(
            avatarPath = character.avatar,
            name = character.name,
            size = 16.dp
        )
        Text(
            text = character.name,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "移除",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier
                .size(12.dp)
                .clickable { onRemove() }
        )
    }
}

/**
 * 日记底栏控制与输入区域
 */
@Composable
fun DiaryInputBar(
    textInput: String,
    onTextInputChange: (String) -> Unit,
    selectedCharacterIds: List<String>,
    characterProfiles: List<CharacterProfile>,
    isLoading: Boolean,
    onRemoveCharacter: (String) -> Unit,
    onAtClicked: () -> Unit,
    onSendClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 已选择角色标签展示Row
        if (selectedCharacterIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "参与者:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedCharacterIds.forEach { charId ->
                            val char = characterProfiles.firstOrNull { it.id == charId }
                            if (char != null) {
                                InputCharacterTag(
                                    character = char,
                                    onRemove = { onRemoveCharacter(charId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 输入框与发送行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // @ 按钮
            IconButton(
                onClick = onAtClicked,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "选择人物",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 文字输入框
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                placeholder = {
                    Text(
                        text = "在此输入今天发生的事或心情感悟...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // 发送按钮 (带 Loading)
            IconButton(
                onClick = onSendClicked,
                enabled = !isLoading,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
