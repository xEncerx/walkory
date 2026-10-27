package com.walkory.app.ui_kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.window.core.layout.WindowSizeClass

@Composable
fun useTwoPanes(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** Selection belongs to navigation, not the window configuration. Resizing never opens a route. */
@Composable
fun WalkoryListDetail(
    expanded: Boolean,
    hasSelection: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    if (expanded) {
        Row(Modifier.fillMaxSize().testTag("two_panes")) {
            Box(Modifier.width(WalkoryLayout.listPaneWidth).fillMaxHeight()) { list() }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxSize()) { detail() }
            }
        }
    } else {
        Box(Modifier.fillMaxSize().testTag("one_pane"), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = WalkoryLayout.contentWidth).fillMaxSize()) {
                if (hasSelection) detail() else list()
            }
        }
    }
}
