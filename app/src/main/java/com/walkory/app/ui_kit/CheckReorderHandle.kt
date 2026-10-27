package com.walkory.app.ui_kit

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

/** Drag visible item centers; callers also expose accessible move controls. */
@Composable
internal fun CheckReorderHandle(key: String, list: LazyListState, disabled: Boolean, description: String, onMove: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    Box(Modifier.sizeIn(minWidth = WalkoryLayout.minimumTouchTarget, minHeight = WalkoryLayout.minimumTouchTarget)
        .semantics { contentDescription = description }
        .pointerInput(key, disabled) {
            if (!disabled) {
                var center = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val item = list.layoutInfo.visibleItemsInfo.find { it.key == key }
                        center = item?.let { it.offset + it.size / 2f } ?: 0f
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        center += delta.y
                        val info = list.layoutInfo
                        val target = info.visibleItemsInfo.find { center >= it.offset && center <= it.offset + it.size }
                        if (target != null && target.key != key) move(key, target.key.toString())
                        val edge = WalkoryLayout.minimumTouchTarget.toPx()
                        val scroll = when {
                            center < info.viewportStartOffset + edge -> -edge / 3f
                            center > info.viewportEndOffset - edge -> edge / 3f
                            else -> 0f
                        }
                        if (scroll != 0f) scope.launch { list.scrollBy(scroll) }
                    },
                )
            }
        }, contentAlignment = Alignment.Center) { WalkoryIcon(Icons.Rounded.DragHandle, description = null) }
}
