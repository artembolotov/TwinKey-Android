package com.artembolotov.twinkey.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Material3 1.4.0 sets anchoredDraggableMotionSpec = DefaultSpatial (underdamped spring) on every
// recomposition via SideEffect. When the sheet is at Expanded and the user flings upward,
// animate(expandedPos, expandedPos, velocity, spring) starts at equilibrium with non-zero velocity
// → overshoots → verticalScaleUp stretches the Surface to full-screen height → visible bounce.
//
// There is no public API to override this. The fix replaces the animationSpec lambda stored inside
// AnchoredDraggableState (which is what settle() actually reads) with a lambda that always returns
// a linear tween, making it immune to Material3's property resets on recomposition.
//
// DisposableEffect keyed on the SheetState instance handles both:
//   • first open   — patch applied once, persists across smart-recompositions of ModalBottomSheet
//   • rotation     — rememberSaveable creates a new SheetState → key changes → effect reruns

private val kTweenSpec: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
private val kTweenLambda: () -> AnimationSpec<Float> = { kTweenSpec }

@OptIn(ExperimentalMaterial3Api::class)
private fun SheetState.fixSettleAnimation() {
    try {
        val stateField = javaClass.getDeclaredField("anchoredDraggableState")
        stateField.isAccessible = true
        val state = stateField.get(this) ?: return
        val specField = state.javaClass.getDeclaredField("animationSpec")
        specField.isAccessible = true
        if (specField.get(state) !== kTweenLambda) {
            specField.set(state, kTweenLambda)
        }
    } catch (_: Exception) {}
}

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
    val sheetState = rememberSaveable(
        saver = SheetState.Saver(
            skipPartiallyExpanded = true,
            positionalThreshold = { screenHeightPx * fraction },
            velocityThreshold = { with(density) { velocityThresholdDp.toPx() } },
            confirmValueChange = { true },
            skipHiddenState = false,
        )
    ) {
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
    ModalBottomSheet(
        sheetState = appSheetState.sheetState,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
    // Keyed on the SheetState instance: reruns automatically when rememberSaveable restores a new
    // instance after a configuration change. The patch is a one-time write per object — subsequent
    // calls hit the reference-equality guard and are free.
    DisposableEffect(appSheetState.sheetState) {
        appSheetState.sheetState.fixSettleAnimation()
        onDispose {}
    }
}
