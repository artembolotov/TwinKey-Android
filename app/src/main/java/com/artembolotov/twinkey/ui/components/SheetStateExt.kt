@file:OptIn(ExperimentalMaterial3Api::class)

package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

class AppSheetState internal constructor(
    internal val sheetState: SheetState,
) {
    suspend fun hide() = sheetState.hide()
}

// Drag thresholds are Material3's own (positional 56.dp, velocity 125.dp/s). The custom ones this
// used to carry — 0.4 of the window height, 600.dp/s — made the sheet 6.6x and 4.8x harder to
// dismiss than stock, so a normal downward drag snapped back instead of closing.
@Composable
fun rememberAppSheetState(): AppSheetState =
    AppSheetState(rememberModalBottomSheetState(skipPartiallyExpanded = true))

@Composable
fun AppModalBottomSheet(
    appSheetState: AppSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val maxHeightDp = remember(containerHeight) { with(density) { containerHeight.toDp() * 0.85f } }
    ModalBottomSheet(
        sheetState = appSheetState.sheetState,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.heightIn(max = maxHeightDp)) {
            content()
        }
    }
}
