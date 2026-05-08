@file:OptIn(ExperimentalMaterial3Api::class)

package com.artembolotov.twinkey.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.lang.reflect.Field

// Material3 resets AnchoredDraggableState.animationSpec to an underdamped spring on every
// recomposition via SideEffect inside anchoredDraggable(). With content near full-screen height,
// any upward swipe overshoots the Expanded anchor, triggering verticalScaleUp — visible bounce.
//
// Fix: replace animationSpec with a tween via our own SideEffect placed inside the content lambda.
// Content is composed after anchoredDraggable() in Material3's layout tree, so our SideEffect
// is registered last and executes after Material3's reset — tween always wins.
private val tweenSpec: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
private val tweenLambda: () -> AnimationSpec<Float> = { tweenSpec }

private val anchoredDraggableStateField: Field? by lazy {
    try { SheetState::class.java.getDeclaredField("anchoredDraggableState").also { it.isAccessible = true } }
    catch (_: Exception) { null }
}
private var animationSpecField: Field? = null

private fun SheetState.fixSettleAnimation() {
    try {
        val draggableState = anchoredDraggableStateField?.get(this) ?: return
        val specField = animationSpecField
            ?: draggableState.javaClass.getDeclaredField("animationSpec").also {
                it.isAccessible = true
                animationSpecField = it
            }
        if (specField.get(draggableState) !== tweenLambda) {
            specField.set(draggableState, tweenLambda)
        }
    } catch (_: Exception) {}
}

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
        SideEffect { appSheetState.sheetState.fixSettleAnimation() }
        Column(modifier = Modifier.heightIn(max = maxHeightDp)) {
            content()
        }
    }
}
