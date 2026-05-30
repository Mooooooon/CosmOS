package com.moonlib.cosmos.ui.twitter

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.moonlib.cosmos.data.twitter.Tweet
import com.moonlib.cosmos.data.twitter.TwitterRepository
import com.moonlib.cosmos.ui.chat.AvatarView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 推特公共主页时间线 Tab
 * 
 * 职责单一：负责展示公共推文列表（仅根推文），卡片化布局，完美融合真实配图与 AI 模拟配图的自适应展示。
 */
@Composable
fun TwitterTimelineTab(
    tweets: List<Tweet>,
    repository: TwitterRepository,
    onTweetClick: (Tweet) -> Unit,
    onDeleteTweet: (Tweet) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootTweets = remember(tweets) {
        tweets.filter { it.parentId == null }.sortedByDescending { it.timestamp }
    }

    if (rootTweets.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🐦 暂无推特动态",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "点击右下角按钮，发布你的第一条推特吧",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rootTweets, key = { it.id }) { tweet ->
                TweetCard(
                    tweet = tweet,
                    repository = repository,
                    onClick = { onTweetClick(tweet) },
                    onDeleteTweet = onDeleteTweet
                )
            }
        }
    }
}

@Composable
fun TweetCard(
    tweet: Tweet,
    repository: TwitterRepository,
    onClick: () -> Unit,
    onDeleteTweet: (Tweet) -> Unit,
    modifier: Modifier = Modifier
) {
    val author = remember(tweet.authorId) { repository.getProfile(tweet.authorId) }
    val authorName = author?.nickname ?: "未知角色"
    val authorUsername = author?.username ?: tweet.authorId
    val avatarPath = author?.avatar ?: ""
    val repliesCount = remember(tweet.id, repository) { repository.getRepliesTo(tweet.id).size }

    val formattedTime = remember(tweet.timestamp) {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINESE)
        sdf.format(Date(tweet.timestamp))
    }

    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { showMenu = true }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 作者头像
            AvatarView(
                avatarPath = avatarPath,
                name = authorName,
                size = 46.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 作者昵称 + 用户名 + 时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = authorName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "@$authorUsername",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // 推文正文
                Text(
                    text = tweet.content,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 渲染配图
                if (tweet.imagePath != null) {
                    TweetImage(imagePath = tweet.imagePath)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 底部动作行（评论数量）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "评论",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (repliesCount > 0) "$repliesCount 条回复" else "回复",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("删除推文", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDeleteTweet(tweet)
                }
            )
        }
    }
}

/**
 * 支持本地图片文件与 AI 模拟描述图片的双重自适应渲染
 */
@Composable
fun TweetImage(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    if (imagePath.startsWith("simulated_image:")) {
        val desc = imagePath.removePrefix("simulated_image:")
        // 渲染高保真、美轮美奂的 AI 模拟图卡
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A), // 深灰蓝
                            Color(0xFF1E293B)  // 渐变过渡
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "图文动态",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AI 拟真画面描述",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "“ $desc ”",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontStyle = FontStyle.Italic,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = "CosmOS Graphics Simulation",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        // 渲染本地已复制好的头像/插图 Bitmap
        val bitmap = remember(imagePath) {
            try {
                val file = File(imagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "配图",
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
