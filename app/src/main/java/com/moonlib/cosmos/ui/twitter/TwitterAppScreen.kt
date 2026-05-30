package com.moonlib.cosmos.ui.twitter

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.profile.CharacterProfileRepository
import com.moonlib.cosmos.data.twitter.Tweet
import com.moonlib.cosmos.data.twitter.TwitterProfile
import com.moonlib.cosmos.data.twitter.TwitterRepository

/**
 * 推特应用主屏幕
 * 
 * 职责单一：负责推特模块的全局页面导航调度（主页、发现、正文盖楼、个人资料编辑）、自适应双 Tab 主控以及发推浮动视窗触发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitterAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { TwitterRepository(context) }
    val systemProfileRepo = remember { CharacterProfileRepository(context) }

    // ── 数据与列表状态 ──────────────────────────────────────────
    var tweetsList by remember { mutableStateOf(repository.getTweets()) }
    var twitterProfiles by remember { mutableStateOf(repository.getProfiles()) }
    val systemProfiles = remember { systemProfileRepo.getProfiles() }

    // ── 导航子状态 ─────────────────────────────────────────────
    var activeTab by remember { mutableIntStateOf(0) } // 0: 主页, 1: 发现
    var activeThreadTweet by remember { mutableStateOf<Tweet?>(null) }
    var editingProfile by remember { mutableStateOf<TwitterProfile?>(null) }
    var showNewTweetDialog by remember { mutableStateOf(false) }

    // ── 双向同步刷新辅助 ────────────────────────────────────────
    fun refreshData() {
        tweetsList = repository.getTweets()
        twitterProfiles = repository.getProfiles()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (activeThreadTweet != null) {
            // 1. 正文盖楼二级详情页面
            TwitterThreadScreen(
                rootTweet = activeThreadTweet!!,
                repository = repository,
                onBackClick = {
                    activeThreadTweet = null
                    refreshData()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (editingProfile != null) {
            // 2. 独立资料编辑二级页面
            TwitterProfileEditScreen(
                profile = editingProfile!!,
                repository = repository,
                onBackClick = {
                    editingProfile = null
                    refreshData()
                },
                onSaveClick = { updatedProfile ->
                    repository.saveProfile(updatedProfile)
                    editingProfile = null
                    refreshData()
                    Toast.makeText(context, "资料保存成功！", Toast.LENGTH_SHORT).show()
                },
                onUnfollowClick = {
                    repository.unfollowCharacter(editingProfile!!.characterId)
                    editingProfile = null
                    refreshData()
                    Toast.makeText(context, "已成功取消关注", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 3. 推特主时间线与发现页面 (Tab 0 & 1)
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "推特 (Twitter)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "退出",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        actions = {
                            // 顶栏右侧快捷编辑个人主页按钮，精致拟真
                            IconButton(
                                onClick = {
                                    val myProf = repository.getProfile("user")
                                    if (myProf != null) {
                                        editingProfile = myProf
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "个人主页",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        windowInsets = WindowInsets(0.dp),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                floatingActionButton = {
                    // 发推悬浮丸形按钮
                    FloatingActionButton(
                        onClick = { showNewTweetDialog = true },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "发布推特",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // 自适应双 Tab 切换栏
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("主页时间线", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("发现关注", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    // 滑动式 Tab 渲染分支
                    Box(modifier = Modifier.weight(1f)) {
                        if (activeTab == 0) {
                            TwitterTimelineTab(
                                tweets = tweetsList,
                                repository = repository,
                                onTweetClick = { activeThreadTweet = it },
                                onDeleteTweet = { toDelete ->
                                    repository.deleteTweet(toDelete.id)
                                    refreshData()
                                    Toast.makeText(context, "推文已删除", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            TwitterDiscoverTab(
                                systemProfiles = systemProfiles,
                                twitterProfiles = twitterProfiles,
                                onFollow = { charId ->
                                    repository.followCharacter(charId)
                                    refreshData()
                                    Toast.makeText(context, "关注成功！该博主已被加入时间线", Toast.LENGTH_SHORT).show()
                                },
                                onEditClick = { editingProfile = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // ── 4. 悬浮发推 Dialog ─────────────────────────────────
        if (showNewTweetDialog) {
            TwitterNewTweetDialog(
                repository = repository,
                onDismiss = { showNewTweetDialog = false },
                onPublishSuccess = {
                    showNewTweetDialog = false
                    refreshData()
                    Toast.makeText(context, "推特发布成功，虚拟时间已推进", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
