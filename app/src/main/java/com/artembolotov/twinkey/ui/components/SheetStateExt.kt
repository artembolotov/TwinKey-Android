package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
class AppSheetState internal constructor(
    internal val sheetState: SheetState,
) {
    suspend fun hide() = sheetState.hide()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppSheetState(
    fraction: Float = 0.4f,
    velocityThresholdDp: Dp = 600.dp,
): AppSheetState {
    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val sheetState = remember {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { screenHeightPx * fraction },
            velocityThreshold = { with(density) { velocityThresholdDp.toPx() } },
        )
    }
    return remember(sheetState) { AppSheetState(sheetState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    appSheetState: AppSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    ModalBottomSheet(
        sheetState = appSheetState.sheetState,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dragHandle = {
            // Full-width box so touches anywhere in the drag handle row are caught,
            // not just on the pill itself.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    keyboardController?.hide()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
        content = content,
    )
}
