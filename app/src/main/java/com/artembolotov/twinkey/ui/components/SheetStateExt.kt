@file:OptIn(ExperimentalMaterial3Api::class)

package com.artembolotov.twinkey.ui.components

import android.util.Log
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
import com.artembolotov.twinkey.BuildConfig
import java.lang.reflect.Field

// Material3 settles the sheet with an underdamped spring: ModalBottomSheet feeds
// SheetState.anchoredDraggableMotionSpec from MotionSchemeKeyTokens.DefaultSpatial, which the
// standard MotionScheme defines as spring(dampingRatio = 0.9f, stiffness = 700f), and
// AnchoredDraggableState.animationSpec is a lambda reading that field back. With content near
// full-screen height, any upward swipe overshoots the Expanded anchor, triggering verticalScaleUp
// — visible bounce. There is no public hook for this one spec: MaterialTheme(motionScheme = …)
// would move the show animation along with the settle, since both read DefaultSpatial.
//
// Fix: replace animationSpec with a tween via our own SideEffect placed inside the content lambda.
// Content is composed after anchoredDraggable() in Material3's layout tree, so our SideEffect
// is registered last and executes after Material3's reset — tween always wins.
//
// Both field names are reached by reflection, so proguard-rules.pro keeps R8 from renaming them.
// Without those rules the lookups fail in release only, which is how this last went unnoticed —
// fixSettleAnimation() now throws in debug and logs in release instead of silently leaving the
// bounce in place.
private val tweenSpec: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
private val tweenLambda: () -> AnimationSpec<Float> = { tweenSpec }

private const val TAG = "SheetStateExt"

private class SettleFailure(val detail: String, val cause: Throwable?)

private val anchoredDraggableStateField: Field? by lazy {
    try {
        SheetState::class.java.getDeclaredField("anchoredDraggableState").also { it.isAccessible = true }
    } catch (_: Exception) {
        null
    }
}
private var animationSpecField: Field? = null

// Returns null once the tween is in place, otherwise what could not be reached. Reporting is left
// to the caller on purpose: a reporter that throws would be swallowed by the catches below.
private fun SheetState.tryFixSettleAnimation(): SettleFailure? {
    val stateField = anchoredDraggableStateField
        ?: return SettleFailure("SheetState.anchoredDraggableState is not reachable", null)
    val draggableState = try {
        stateField.get(this)
            ?: return SettleFailure("SheetState.anchoredDraggableState is null", null)
    } catch (e: Exception) {
        return SettleFailure("SheetState.anchoredDraggableState could not be read", e)
    }
    val specField = animationSpecField ?: try {
        draggableState.javaClass.getDeclaredField("animationSpec").also {
            it.isAccessible = true
            animationSpecField = it
        }
    } catch (e: Exception) {
        return SettleFailure("${draggableState.javaClass.name}.animationSpec is not reachable", e)
    }
    return try {
        if (specField.get(draggableState) !== tweenLambda) {
            specField.set(draggableState, tweenLambda)
        }
        null
    } catch (e: Exception) {
        SettleFailure("${draggableState.javaClass.name}.animationSpec could not be written", e)
    }
}

// The SideEffect below runs on every recomposition, so report a broken lookup only once.
private var settleAnimationFailureReported = false

private fun SheetState.fixSettleAnimation() {
    val failure = tryFixSettleAnimation() ?: return
    if (settleAnimationFailureReported) return
    settleAnimationFailureReported = true
    val message = "Sheet settle animation stays on the Material3 spring — sheets will bounce on " +
        "an upward swipe (${failure.detail}). Either Material3 renamed the field or the keep " +
        "rules for it in proguard-rules.pro no longer match."
    if (BuildConfig.DEBUG) throw IllegalStateException(message, failure.cause)
    Log.e(TAG, message, failure.cause)
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
