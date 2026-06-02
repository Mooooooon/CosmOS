package com.moonlib.cosmos.ui.interaction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * 实体动作互动 APP 导航密封接口
 */
sealed interface InteractionNavigation {
    object List : InteractionNavigation
    object Multi : InteractionNavigation
    data class Conversation(val characterId: String) : InteractionNavigation
    object Settings : InteractionNavigation // 新增设置页面导航
}

/**
 * 实体动作互动 APP 根级容器组件
 *
 * 职责单一：作为互动应用的主导航控制器，管理页面跳转路由栈并拦截系统物理返回手势。
 */
@Composable
fun InteractionAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 页面路由栈，初始状态为“角色列表”
    var navStack by remember { mutableStateOf(listOf<InteractionNavigation>(InteractionNavigation.List)) }

    val currentScreen = navStack.last()

    // 统一页面跳转
    val navigateTo: (InteractionNavigation) -> Unit = { screen ->
        navStack = navStack + screen
    }

    // 统一回退逻辑
    val goBack: () -> Unit = {
        if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
        } else {
            onGoBack() // 回退至首页，退回虚拟桌面
        }
    }

    // 拦截物理/虚拟返回键，实现 APP 内级联回退
    BackHandler(enabled = true) {
        goBack()
    }

    // 高端精美的淡入淡出子页面过渡效果
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn().togetherWith(fadeOut())
        },
        label = "InteractionSubScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            is InteractionNavigation.List -> {
                InteractionListScreen(
                    onNavigateTo = navigateTo,
                    onExitApp = onGoBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is InteractionNavigation.Conversation -> {
                InteractionConversationScreen(
                    characterId = screen.characterId,
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is InteractionNavigation.Multi -> {
                MultiInteractionScreen(
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is InteractionNavigation.Settings -> {
                InteractionSettingsScreen(
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
