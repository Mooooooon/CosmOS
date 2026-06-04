package com.moonlib.cosmos.ui.desktop

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/**
 * 单个桌面网格槽位。
 *
 * 职责单一：呈现槽位内容并将长按拖动手势传递给网格。
 */
@Composable
fun DraggableDesktopSlot(
    app: DesktopApp?,
    slotHeight: Dp,
    isDragging: Boolean,
    showDropRange: Boolean,
    isTarget: Boolean,
    dragOffset: Offset,
    onAppClick: (DesktopApp) -> Unit,
    onDragStart: (DesktopApp) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(slotHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                }
            }
            .then(
                if (app != null) {
                    Modifier.pointerInput(app.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(app) },
                            onDragCancel = onDragEnd,
                            onDragEnd = onDragEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        DesktopDropSlot(
            visible = showDropRange,
            isTarget = isTarget,
        )
        if (app != null) {
            AppIconItem(
                app = app,
                onClick = { onAppClick(app) },
                isDragging = isDragging,
            )
        }
    }
}
