package com.moonlib.cosmos.ui.twitter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import com.moonlib.cosmos.data.twitter.TwitterProfile
import com.moonlib.cosmos.ui.chat.AvatarView

/**
 * 发现与关注管理 Tab
 * 
 * 职责单一：负责展示“推荐关注的系统人物”与“已关注博主列表”，提供关注/取消关注开关及跳转独立修改主页资料的入口。
 */
@Composable
fun TwitterDiscoverTab(
    systemProfiles: List<CharacterProfile>,
    twitterProfiles: List<TwitterProfile>,
    onFollow: (String) -> Unit,
    onEditClick: (TwitterProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    // 过滤出未关注的角色
    val notFollowed = systemProfiles.filter { systemChar ->
        !systemChar.isPlayer && twitterProfiles.none { it.characterId == systemChar.id && it.isFollowed }
    }

    // 已关注的 NPC 推特账号
    val followedNpcs = twitterProfiles.filter { it.isFollowed && it.characterId != "user" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. 发现新面孔（未关注列表） ──────────────────────────
        if (notFollowed.isNotEmpty()) {
            item {
                Text(
                    text = "✨ 推荐关注",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(notFollowed, key = { it.id }) { char ->
                WhoToFollowCard(
                    character = char,
                    onFollowClick = { onFollow(char.id) }
                )
            }
        }

        // ── 2. 已关注博主列表（支持编辑与取消关注） ────────────────
        if (followedNpcs.isNotEmpty()) {
            item {
                Text(
                    text = "👥 我的关注 (${followedNpcs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(followedNpcs, key = { it.characterId }) { profile ->
                FollowedNpcCard(
                    profile = profile,
                    onEditClick = { onEditClick(profile) }
                )
            }
        }

        // ── 3. 空白兜底指示 ──────────────────────────────────
        if (notFollowed.isEmpty() && followedNpcs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎭 暂无推荐博主",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "请先在【档案 App】中新建角色人设以供关注",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 推荐关注卡片
 */
@Composable
fun WhoToFollowCard(
    character: CharacterProfile,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarView(
                avatarPath = character.avatar,
                name = character.name,
                size = 40.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "系统档案角色",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // 极简高对比度关注按钮
            Button(
                onClick = onFollowClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "关注",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "关注",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 已关注角色卡片（支持编辑主页资料与快捷展示用户名/简介）
 */
@Composable
fun FollowedNpcCard(
    profile: TwitterProfile,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarView(
                    avatarPath = profile.avatar,
                    name = profile.nickname,
                    size = 42.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.nickname,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "@${profile.username}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // 编辑资料按钮
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑资料",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 简介预览
            if (profile.bio.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = profile.bio,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
