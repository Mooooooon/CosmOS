package com.moonlib.cosmos.ui.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moonlib.cosmos.data.desktop.DesktopLayoutRepository
import kotlin.math.ceil

private const val DESKTOP_COLUMN_COUNT = 4
private val DesktopSlotHeight = 96.dp
private val DesktopSlotSpacing = 4.dp
private val DesktopGridVerticalPadding = 8.dp

/**
 * App 图标网格。
 *
 * 使用时钟下方的全部空间创建四列槽位，并处理长按拖动与位置持久化。
 * 职责单一：负责桌面网格布局与图标位置管理，不包含图标的具体视觉实现。
 */
@Composable
fun AppGrid(
    onAppClick: (DesktopApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val layoutRepository = remember {
        DesktopLayoutRepository(context.applicationContext)
    }

    BoxWithConstraints(modifier = modifier) {
        val minimumRows = ceil(desktopApps.size / DESKTOP_COLUMN_COUNT.toFloat()).toInt()
        val availableHeight = maxHeight - DesktopGridVerticalPadding * 2 + DesktopSlotSpacing
        val visibleRows = (availableHeight / (DesktopSlotHeight + DesktopSlotSpacing))
            .toInt()
            .coerceAtLeast(minimumRows)
        val slotCount = visibleRows * DESKTOP_COLUMN_COUNT
        val slots = remember(slotCount) {
            mutableStateListOf<DesktopApp?>().apply {
                addAll(layoutRepository.loadSlots(desktopApps, slotCount))
            }
        }
        val gridState = rememberLazyGridState()

        var draggedAppId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var targetIndex by remember { mutableStateOf<Int?>(null) }
        var pendingTargetIndex by remember { mutableStateOf<Int?>(null) }

        fun finishDragging() {
            if (draggedAppId != null) {
                layoutRepository.saveSlots(slots)
            }
            draggedAppId = null
            dragOffset = Offset.Zero
            targetIndex = null
            pendingTargetIndex = null
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(DESKTOP_COLUMN_COUNT),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = DesktopGridVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(DesktopSlotSpacing),
            userScrollEnabled = false,
        ) {
            itemsIndexed(
                items = slots,
                key = { index, app -> app?.id ?: "empty_slot_$index" },
            ) { index, app ->
                val isDragging = app?.id == draggedAppId

                DraggableDesktopSlot(
                    app = app,
                    slotHeight = DesktopSlotHeight,
                    isDragging = isDragging,
                    showDropRange = draggedAppId != null,
                    isTarget = targetIndex == index,
                    dragOffset = dragOffset,
                    onAppClick = {
                        if (draggedAppId == null) {
                            onAppClick(it)
                        }
                    },
                    onDragStart = {
                        draggedAppId = it.id
                        dragOffset = Offset.Zero
                        pendingTargetIndex = null
                    },
                    onDragEnd = ::finishDragging,
                    onDrag = { dragAmount ->
                        val currentItem = gridState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == app?.id }
                            ?: return@DraggableDesktopSlot
                        val nextOffset = dragOffset + dragAmount
                        val pendingIndex = pendingTargetIndex
                        if (pendingIndex != null && currentItem.index != pendingIndex) {
                            dragOffset = nextOffset
                            return@DraggableDesktopSlot
                        }
                        pendingTargetIndex = null

                        val draggedCenter = Offset(
                            x = currentItem.offset.x + nextOffset.x + currentItem.size.width / 2f,
                            y = currentItem.offset.y + nextOffset.y + currentItem.size.height / 2f,
                        )
                        val targetItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            draggedCenter.x >= item.offset.x &&
                                draggedCenter.x <= item.offset.x + item.size.width &&
                                draggedCenter.y >= item.offset.y &&
                                draggedCenter.y <= item.offset.y + item.size.height
                        }

                        targetIndex = targetItem?.index
                        if (targetItem == null || targetItem.index == currentItem.index) {
                            dragOffset = nextOffset
                        } else {
                            dragOffset = nextOffset + Offset(
                                x = (currentItem.offset.x - targetItem.offset.x).toFloat(),
                                y = (currentItem.offset.y - targetItem.offset.y).toFloat(),
                            )
                            pendingTargetIndex = targetItem.index
                            slots.swap(currentItem.index, targetItem.index)
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun <T> MutableList<T>.swap(firstIndex: Int, secondIndex: Int) {
    val first = this[firstIndex]
    this[firstIndex] = this[secondIndex]
    this[secondIndex] = first
}
