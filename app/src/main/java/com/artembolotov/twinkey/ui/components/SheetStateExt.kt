@file:OptIn(ExperimentalMaterial3Api::class)

package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class AppSheetState internal constructor(
    internal val sheetState: SheetState,
) {
    suspend fun hide() = sheetState.hide()
}

@Composable
fun rememberAppSheetState(
    fraction: Float = 0.4f,
    velocityThresholdDp: Dp = 600.dp,
): AppSheetState {
    val density = LocalDensity.current
    val containerHeight = LocalWindowInfo.current.containerSize.height
    return remember {
        AppSheetState(SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { containerHeight * fraction },
            velocityThreshold = { with(density) { velocityThresholdDp.toPx() } },
        ))
    }
}

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
