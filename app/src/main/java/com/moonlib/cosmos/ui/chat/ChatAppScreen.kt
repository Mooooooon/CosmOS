package com.moonlib.cosmos.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * 聊天 APP 导航密封接口
 */
sealed interface ChatNavigation {
    object Main : ChatNavigation
    data class EditContact(val contactId: String? = null) : ChatNavigation
    data class InfoCard(val contactId: String) : ChatNavigation
    data class Conversation(val contactId: String) : ChatNavigation
    data class MomentThread(val momentId: String) : ChatNavigation
}

/**
 * 聊天 APP 根级容器组件
 *
 * 职责单一：作为聊天应用的主导航控制器，管理页面跳转路由栈并拦截系统物理返回手势。
 */
@Composable
fun ChatAppScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 页面路由栈，初始状态为“主页（消息与联系人）”
    var navStack by remember { mutableStateOf(listOf<ChatNavigation>(ChatNavigation.Main)) }

    // 将主页的 activeTab 状态提升到根容器中管理，防止从动态二级页面返回时 Tab 状态重置为 0
    var activeTab by remember { mutableStateOf(0) }

    val currentScreen = navStack.last()

    // 统一下步跳转
    val navigateTo: (ChatNavigation) -> Unit = { screen ->
        navStack = navStack + screen
    }

    // 统一回退逻辑
    val goBack: () -> Unit = {
        if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
        } else {
            onGoBack() // 已回退至首页，再次点击返回则退回虚拟桌面
        }
    }

    // 拦截物理/虚拟返回键，实现 APP 内级联回退
    BackHandler(enabled = true) {
        goBack()
    }

    // 精美高雅的淡入淡出子页面过渡效果
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn().togetherWith(fadeOut())
        },
        label = "ChatSubScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            is ChatNavigation.Main -> {
                ChatMainScreen(
                    activeTab = activeTab,
                    onActiveTabChange = { activeTab = it },
                    onNavigateTo = navigateTo,
                    onExitApp = onGoBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ChatNavigation.EditContact -> {
                ContactEditScreen(
                    contactId = screen.contactId,
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ChatNavigation.InfoCard -> {
                ContactInfoCardScreen(
                    contactId = screen.contactId,
                    onNavigateTo = navigateTo,
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ChatNavigation.Conversation -> {
                ChatConversationScreen(
                    contactId = screen.contactId,
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ChatNavigation.MomentThread -> {
                ChatMomentThreadScreen(
                    momentId = screen.momentId,
                    onGoBack = goBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
