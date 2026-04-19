package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
class AppSheetState internal constructor(
    internal val sheetState: SheetState,
    private val heightPx: MutableFloatState,
) {
    suspend fun hide() = sheetState.hide()

    internal fun onMeasured(height: Int) { heightPx.floatValue = height.toFloat() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppSheetState(
    fraction: Float = 0.4f,
    velocityThresholdDp: Dp = 600.dp,
): AppSheetState {
    val density = LocalDensity.current
    val heightPx = rememberSaveable { mutableFloatStateOf(0f) }
    val sheetState = rememberSaveable(
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = { heightPx.floatValue * fraction },
            velocityThreshold = { with(density) { velocityThresholdDp.toPx() } },
            confirmValueChange = { true },
            skipHiddenState = false,
        )
    ) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { heightPx.floatValue * fraction },
            velocityThreshold = { with(density) { velocityThresholdDp.toPx() } },
        )
    }
    return remember(sheetState, heightPx) { AppSheetState(sheetState, heightPx) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    appSheetState: AppSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        sheetState = appSheetState.sheetState,
        onDismissRequest = onDismissRequest,
        modifier = modifier.onSizeChanged { appSheetState.onMeasured(it.height) },
        content = content,
    )
}
