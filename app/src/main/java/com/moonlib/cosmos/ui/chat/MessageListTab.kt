package com.moonlib.cosmos.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
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
import com.moonlib.cosmos.data.chat.ChatContact
import com.moonlib.cosmos.data.chat.ChatMessage
import com.moonlib.cosmos.data.chat.ChatRepository
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息会话包装实体
 */
private data class ActiveChatSession(
    val contact: ChatContact,
    val lastMessage: ChatMessage,
    val formattedTime: String
)

/**
 * 聊天 APP - 消息会话列表 Tab 面板
 *
 * 职责单一：负责拉取所有联系人的消息纪录，动态生成排序好的活跃会话列表并渲染。
 */
@Composable
fun MessageListTab(
    onNavigateTo: (ChatNavigation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatRepo = remember { ChatRepository(context) }
    
    // 动态检索有历史聊天记录的会话
    val activeSessions = remember {
        val allContacts = chatRepo.getContacts()
        val sessions = mutableListOf<ActiveChatSession>()
        
        for (contact in allContacts) {
            val messages = chatRepo.getMessages(contact.id)
            if (messages.isNotEmpty()) {
                val lastMsg = messages.last()
                sessions.add(
                    ActiveChatSession(
                        contact = contact,
                        lastMessage = lastMsg,
                        formattedTime = formatMessageTime(lastMsg.timestamp)
                    )
                )
            }
        }
        
        // 按照最后一条消息的时间戳从新到旧（降序）排列
        sessions.sortByDescending { it.lastMessage.timestamp }
        sessions
    }

    if (activeSessions.isEmpty()) {
        // 无消息记录状态提示
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = "无聊天会话",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "星河深邃，尚未收到消息",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "前往联系人列表中，随便挑选一位知己，发送第一条消息开启奇妙连结吧！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp
            )
        }
    } else {
        // 消息列表
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = activeSessions,
                key = { it.contact.id }
            ) { session ->
                MessageSessionItem(
                    session = session,
                    onClick = {
                        onNavigateTo(ChatNavigation.Conversation(session.contact.id))
                    }
                )
            }
        }
    }
}

/**
 * 消息会话项条目组件
 */
@Composable
private fun MessageSessionItem(
    session: ActiveChatSession,
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
            avatarPath = session.contact.avatar,
            name = session.contact.nickname,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 昵称与消息
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = session.contact.nickname,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = session.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = session.lastMessage.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 格式化消息时间戳的辅助函数
 */
private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val date = Date(timestamp)
    
    val calNow = Calendar.getInstance()
    val calMsg = Calendar.getInstance()
    calNow.timeInMillis = now
    calMsg.timeInMillis = timestamp
    
    return if (calNow.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR) &&
        calNow.get(Calendar.DAY_OF_YEAR) == calMsg.get(Calendar.DAY_OF_YEAR)) {
        // 如果是今天，格式化为 "HH:mm"
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else if (calNow.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR) &&
        calNow.get(Calendar.DAY_OF_YEAR) - calMsg.get(Calendar.DAY_OF_YEAR) == 1) {
        // 如果是昨天
        "昨天"
    } else {
        // 其它时间显示 "MM-dd"
        SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
    }
}
