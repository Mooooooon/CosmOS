package com.moonlib.cosmos.ui.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.ui.profile.ProfileAppScreen
import com.moonlib.cosmos.ui.settings.SettingsAppScreen
import com.moonlib.cosmos.ui.chat.ChatAppScreen
import com.moonlib.cosmos.ui.time.TimeAppScreen
import com.moonlib.cosmos.ui.diary.DiaryAppScreen
import com.moonlib.cosmos.ui.memory.MemoryAppScreen
import com.moonlib.cosmos.ui.twitter.TwitterAppScreen
import com.moonlib.cosmos.ui.theme.*
import kotlin.random.Random

/**
 * 虚拟手机桌面主屏
 *
 * 布局结构（由外到内）：
 *   Box（全屏）
 *   ├── SpaceWallpaper      — 壁纸，matchParentSize，最底层
 *   └── Column（全屏）       — 内容层，从屏幕 y=0 开始
 *       ├── VirtualStatusBar — 第一个子项，天然贴顶
 *       └── AnimatedContent  — 主显示区，支持在桌面（钟表+图标网格）与打开的 APP 之间滑动切换
 *
 * 系统状态栏已在 MainActivity 中完全隐藏。
 * 使用 [WindowInsets.displayCutout] 获取刘海/打孔屏顶部安全区高度。
 */
@Composable
fun DesktopScreen() {
    var activeAppId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 1. 深空星云壁纸（最底层，覆盖全屏含状态栏区域）────
        SpaceWallpaper(modifier = Modifier.matchParentSize())

        val isDark = LocalThemeConfig.current.isDark
        val columnBg = when (activeAppId) {
            null -> Color.Transparent
            "chat" -> if (isDark) Color.Black else Color.White
            else -> MaterialTheme.colorScheme.background
        }

        // ── 2. 内容列（从屏幕顶部 y=0 开始）──────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(columnBg)
                .navigationBarsPadding(),        // 底部让出导航栏
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 作为 Column 第一个子项，天然贴屏幕顶部 y=0，内置精致高度
            VirtualStatusBar()

            // ── 3. 主显示区域切换（从上下滚动优化为高雅的放大缩小与淡入淡出过渡） ──────────────
            AnimatedContent(
                targetState = activeAppId,
                transitionSpec = {
                    val duration = 240
                    (fadeIn(animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.93f, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.93f, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing))
                        )
                },
                label = "AppSwitchTransition",
                modifier = Modifier.fillMaxSize().weight(1f)
            ) { appId ->
                if (appId == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        DesktopClock(modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(24.dp))

                        AppGrid(
                            onAppClick = { app ->
                                activeAppId = app.id
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 24.dp),
                        )
                    }
                } else if (appId == "settings") {
                    SettingsAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "profile") {
                    ProfileAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "chat") {
                    ChatAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "interaction") {
                    com.moonlib.cosmos.ui.interaction.InteractionAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "time") {
                    TimeAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "diary") {
                    DiaryAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "memory") {
                    MemoryAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (appId == "twitter") {
                    TwitterAppScreen(
                        onGoBack = { activeAppId = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * 深空星云壁纸
 *
 * 使用 Canvas 绘制竖向渐变背景 + 固定 seed 随机星点，无需外部图片资源。
 * 职责单一：只负责背景视觉绘制。
 */
@Composable
private fun SpaceWallpaper(modifier: Modifier = Modifier) {
    val isDark = LocalThemeConfig.current.isDark
    val stars = remember {
        val rng = Random(seed = 42)
        List(180) {
            Triple(
                rng.nextFloat(),
                rng.nextFloat(),
                rng.nextFloat() * 2f + 0.5f,
            )
        }
    }

    Canvas(modifier = modifier) {
        if (isDark) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f   to SpaceDeepBlack,
                    0.4f to SpaceNavy,
                    0.7f to SpaceIndigo,
                    1f   to SpaceDeepBlack,
                ),
                size = size,
            )
            for ((xRatio, yRatio, radius) in stars) {
                drawCircle(
                    color  = StarWhite.copy(alpha = 0.4f + xRatio * 0.4f),
                    radius = radius,
                    center = Offset(xRatio * size.width, yRatio * size.height),
                )
            }
        } else {
            // 晨曦极光浅色渐变
            drawRect(
                brush = Brush.verticalGradient(
                    0f   to Color(0xFFEBEFFE),
                    0.5f to Color(0xFFE0E7FF),
                    1f   to Color(0xFFF3F4F6),
                ),
                size = size,
            )
            for ((xRatio, yRatio, radius) in stars) {
                drawCircle(
                    color  = NebulaPurple.copy(alpha = 0.06f + xRatio * 0.1f),
                    radius = radius * 1.5f,
                    center = Offset(xRatio * size.width, yRatio * size.height),
                )
            }
        }
    }
}
