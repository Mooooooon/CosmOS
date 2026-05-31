package com.moonlib.cosmos.ui.chat

// import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.chat.ChatRepository
import com.moonlib.cosmos.data.profile.CharacterProfileRepository

/**
 * 聊天 APP - 联系人资料卡页面
 *
 * 职责单一：渲染联系人的详细背景资料，并提供“编辑资料”、“发送消息”及“删除”交互入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoCardScreen(
    contactId: String,
    onNavigateTo: (ChatNavigation) -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatRepo = remember { ChatRepository(context) }
    val profileRepo = remember { CharacterProfileRepository(context) }

    // 1. 获取当前联系人数据
    val contact = remember(contactId) {
        chatRepo.getContacts().firstOrNull { it.id == contactId }
    }

    if (contact == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("联系人不存在或已被删除", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    // 2. 匹配关联的角色档案信息
    val matchedProfile = remember(contact.characterId) {
        profileRepo.getProfiles().firstOrNull { it.id == contact.characterId }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("详细资料", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 精美删除按钮作为辅助操作
                    IconButton(
                        onClick = {
                            chatRepo.deleteContact(contactId)
                            // Toast.makeText(context, "联系人已删除", Toast.LENGTH_SHORT).show()
                            onGoBack() // 成功删除后自动回退至列表页
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除联系人",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // ─── 1. 资料卡核心背景卡片 ──────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 大头像
                    AvatarView(
                        avatarPath = contact.avatar,
                        name = contact.nickname,
                        size = 96.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 昵称
                    Text(
                        text = contact.nickname,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 个性签名
                    Text(
                        text = contact.signature.ifBlank { "这个密友很低调，暂无签名" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 2. 档案绑定详情卡片 ────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 档案联动信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "联动角色档案",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = matchedProfile?.name ?: "未关联档案",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // 档案介绍
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "角色简述",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (matchedProfile != null) {
                                matchedProfile.prompt.take(120).trim() + if (matchedProfile.prompt.length > 120) "..." else ""
                            } else {
                                "无关联背景，AI将采用默认回复"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ─── 3. 底部双强力交互按钮 ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 编辑资料按钮
                OutlinedButton(
                    onClick = { onNavigateTo(ChatNavigation.EditContact(contactId)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    )
                ) {
                    Text(
                        text = "编辑资料",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // 发送消息按钮 (重点突出)
                Button(
                    onClick = { onNavigateTo(ChatNavigation.Conversation(contactId)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "发送消息",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
