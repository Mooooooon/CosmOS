package com.moonlib.cosmos.ui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App 图标网格
 *
 * 以 4 列网格排布 [desktopApps] 中的所有 App 图标。
 * 职责单一：只负责网格布局，不包含图标的具体视觉实现。
 */
@Composable
fun AppGrid(
    onAppClick: (DesktopApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns             = GridCells.Fixed(4),
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled   = false,   // 桌面不需要滚动（图标数量固定）
    ) {
        items(
            items = desktopApps,
            key   = { it.id },
        ) { app ->
            Box(contentAlignment = Alignment.Center) {
                AppIconItem(
                    app     = app,
                    onClick = { onAppClick(app) },
                )
            }
        }
    }
}
