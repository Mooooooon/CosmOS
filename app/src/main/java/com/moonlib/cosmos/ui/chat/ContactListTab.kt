package com.moonlib.cosmos.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.chat.ChatRepository

/**
 * 聊天 APP - 联系人列表 Tab 面板
 *
 * 职责单一：负责从仓库加载联系人，并以列表形式渲染。包含完美的空白态提示。
 */
@Composable
fun ContactListTab(
    onNavigateTo: (ChatNavigation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatRepo = remember { ChatRepository(context) }
    val contacts = remember { chatRepo.getContacts() }

    if (contacts.isEmpty()) {
        // 空列表状态提示
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "无联系人",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "星河寂静，暂无联系人",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击右上角加号按钮，将档案中的角色转化为你的聊天密友吧！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp
            )
        }
    } else {
        // 渲染联系人列表
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = contacts,
                key = { it.id }
            ) { contact ->
                ContactItem(
                    contact = contact,
                    onClick = {
                        onNavigateTo(ChatNavigation.InfoCard(contact.id))
                    }
                )
            }
        }
    }
}

/**
 * 单个联系人列表项组件
 */
@Composable
private fun ContactItem(
    contact: com.moonlib.cosmos.data.chat.ChatContact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        AvatarView(
            avatarPath = contact.avatar,
            name = contact.nickname,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 昵称与签名
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.nickname,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = contact.signature.ifBlank { "这个神秘人什么都没留下~" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
        }
    }
}
