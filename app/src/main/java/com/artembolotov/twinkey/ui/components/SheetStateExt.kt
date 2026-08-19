@file:OptIn(ExperimentalMaterial3Api::class)

package com.artembolotov.twinkey.ui.components

import android.util.Log
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.artembolotov.twinkey.BuildConfig
import java.lang.reflect.Field

// Material3 settles a sheet drag with MotionSchemeKeyTokens.DefaultSpatial, which the standard
// motion scheme defines as spring(dampingRatio = 0.9f, stiffness = 700f). Underdamped, so a fast
// upward flick overshoots the Expanded anchor; verticalScaleUp then stretches the sheet from its
// top edge until it settles back.
//
// We settle on MotionSchemeKeyTokens.FastEffects instead — spring(dampingRatio = 1f,
// stiffness = 3800f), the same token ModalBottomSheet already uses for hideMotionSpec. Critical
// damping alone is not enough: a spring released at its target with leftover velocity still makes
// one excursion, its size proportional to velocity/sqrt(stiffness). Only the stiff critically
// damped pair removes it outright. Top edge deviation from rest, hardest flick we can synthesise
// (1400 px in 100 ms), measured frame by frame at 60 fps:
//
//     spring(0.9, 700)   Material default   102 px over 200 ms
//     spring(1.0, 700)   critical, soft      40 px over 100 ms
//     spring(1.0, 3800)  FastEffects          0 px
private val settleSpec: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 3800f)
private val settleSpecLambda: () -> AnimationSpec<Float> = { settleSpec }

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

// Returns null once our spec is in place, otherwise what could not be reached. Reporting is left to
// the caller on purpose: a reporter that throws would be swallowed by the catches below.
private fun SheetState.installSettleSpec(): SettleFailure? {
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
        specField.set(draggableState, settleSpecLambda)
        if (specField.get(draggableState) !== settleSpecLambda) {
            SettleFailure("${draggableState.javaClass.name}.animationSpec did not keep the value", null)
        } else {
            null
        }
    } catch (e: Exception) {
        SettleFailure("${draggableState.javaClass.name}.animationSpec could not be written", e)
    }
}

private var settleFailureReported = false

private fun report(failure: SettleFailure?) {
    if (failure == null || settleFailureReported) return
    settleFailureReported = true
    val message = "Sheet settle animation stays on the Material3 spring — sheets will overshoot on " +
        "an upward flick (${failure.detail}). Either Material3 changed the field or the keep rules " +
        "for it in proguard-rules.pro no longer match."
    if (BuildConfig.DEBUG) throw IllegalStateException(message, failure.cause)
    Log.e(TAG, message, failure.cause)
}

@Composable
fun rememberAppSheetState(): AppSheetState {
    // Thresholds, saver and every other parameter stay Material3's own.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Applied once per SheetState, not from a SideEffect on every recomposition. In material3 1.4.0
    // AnchoredDraggableState.animationSpec is a val, set in the constructor to a lambda reading
    // SheetState.anchoredDraggableMotionSpec. ModalBottomSheet rewrites that SheetState property on
    // every recomposition but never the lambda, so one write holds. The earlier version re-applied
    // it continuously and raced Material3 mid-gesture, which 5539822 blames for visible jerkiness.
    // rememberModalBottomSheetState builds a new SheetState after a config change, and the key
    // re-runs this with it.
    DisposableEffect(sheetState) {
        report(sheetState.installSettleSpec())
        onDispose {}
    }
    return remember(sheetState) { AppSheetState(sheetState) }
}

class AppSheetState internal constructor(
    internal val sheetState: SheetState,
) {
    suspend fun hide() = sheetState.hide()
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
