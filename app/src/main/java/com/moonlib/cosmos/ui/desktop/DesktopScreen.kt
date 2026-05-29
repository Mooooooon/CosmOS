package com.moonlib.cosmos.ui.desktop

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.ui.settings.SettingsAppScreen
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

        // ── 2. 内容列（从屏幕顶部 y=0 开始）──────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),        // 底部让出导航栏
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 作为 Column 第一个子项，天然贴屏幕顶部 y=0，内置精致高度
            VirtualStatusBar()

            // ── 3. 主显示区域切换（含滑入滑出过渡动效） ──────────────
            AnimatedContent(
                targetState = activeAppId,
                transitionSpec = {
                    (slideInVertically(initialOffsetY = { it }) + fadeIn())
                        .togetherWith(slideOutVertically(targetOffsetY = { it }) + fadeOut())
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

                        Spacer(modifier = Modifier.weight(1f))

                        AppGrid(
                            onAppClick = { app ->
                                if (app.id == "settings") {
                                    activeAppId = "settings"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        )
                    }
                } else if (appId == "settings") {
                    SettingsAppScreen(
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
    }
}
