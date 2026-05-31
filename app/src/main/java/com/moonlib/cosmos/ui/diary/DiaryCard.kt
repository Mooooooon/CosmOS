package com.moonlib.cosmos.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.diary.DiaryEntry
import com.moonlib.cosmos.data.profile.CharacterProfile
import com.moonlib.cosmos.ui.chat.AvatarView

/**
 * 日记信纸感时间线卡片
 */
@Composable
fun DiaryCard(
    diary: DiaryEntry,
    characterProfiles: List<CharacterProfile>,
    isStatusCardEnabled: Boolean,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit
) {
    // 状态卡激活的人称头像Tab切换
    var activeTabCharId by remember { mutableStateOf(diary.involvedCharacterIds.firstOrNull()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶栏：虚拟时间（含时间跳转展示）+ 菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间展示：若有跳转目标时间则显示 "开始 → 结束"，否则只显示开始时间
                val timeDisplay = if (diary.nextVirtualTime.isNotBlank()) {
                    // 只取日期+时分，去掉秒
                    val startShort = diary.virtualTime.substringBeforeLast(":")
                        .let { if (it.length > 16) it.take(16) else it }
                    "$startShort  →  ${diary.nextVirtualTime}"
                } else {
                    diary.virtualTime
                }
                Text(
                    text = timeDisplay,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )

                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("重新生成", fontSize = 13.sp) },
                            onClick = {
                                menuExpanded = false
                                onRegenerate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 用户发送的引子/起因
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🌱 引子: ${diary.playerInput}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 日记正文
            Text(
                text = diary.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 参与人小头像Row + 状态卡渲染
            val involvedChars = remember(diary.involvedCharacterIds, characterProfiles) {
                characterProfiles.filter { diary.involvedCharacterIds.contains(it.id) }
            }

            if (involvedChars.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 人物列表标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        involvedChars.forEach { char ->
                            AvatarView(
                                avatarPath = char.avatar,
                                name = char.name,
                                size = 22.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "参与剧情",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }

                // 底部状态卡逻辑 (当本篇日记里存有状态快照，且应用开启了状态卡显示时)
                val hasStatusSnapshot = diary.statusMap.isNotEmpty()
                if (isStatusCardEnabled && hasStatusSnapshot) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 状态卡人物 Tab 切换丸按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        involvedChars.forEach { char ->
                            val isActive = activeTabCharId == char.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    )
                                    .clickable { activeTabCharId = char.id }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AvatarView(
                                    avatarPath = char.avatar,
                                    name = char.name,
                                    size = 14.dp
                                )
                                Text(
                                    text = char.name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 展示所选角色的状态属性快照网格
                    val selectedCharId = activeTabCharId ?: diary.involvedCharacterIds.firstOrNull()
                    val charStatus = diary.statusMap[selectedCharId]

                    if (charStatus != null && charStatus.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            charStatus.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "📍 $key: ",
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.width(70.dp)
                                    )
                                    Text(
                                        text = value,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "该角色在本篇日记中状态未变更",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
