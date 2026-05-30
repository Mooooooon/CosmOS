package com.moonlib.cosmos.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * 会话类页面的键盘避让策略：顶部栏保持固定，底部输入栏贴着键盘，只有中间内容随 IME 缩小。
 */
fun Modifier.conversationInputInsets(): Modifier =
    this
        .navigationBarsPadding()
        .imePadding()

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.conversationContentImeResize(): Modifier =
    this.imeNestedScroll()

@Composable
fun rememberImeVisible(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.ime.getBottom(density) > 0
}
