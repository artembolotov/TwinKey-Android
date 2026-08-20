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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.artembolotov.twinkey.BuildConfig
import java.lang.reflect.Field

// This is not cosmetic. Without it a sheet can get stuck oscillating, permanently, in landscape.
//
// Material3 settles a sheet drag with MotionSchemeKeyTokens.DefaultSpatial, which the standard
// motion scheme defines as spring(dampingRatio = 0.9f, stiffness = 700f). Underdamped, so
// releasing a flick overshoots the Expanded anchor. Once the offset is above the anchor,
// verticalScaleUp stretches the sheet from its top edge. In landscape the content is nearly as
// tall as the window, so the stretched sheet measures taller, which moves the anchor, which
// overshoots again: the loop never converges. The sheet inflates to fill the screen and ping-pongs
// between a handful of positions indefinitely, at a steady 60 fps against an 8 fps idle.
//
// The overshoot is the only way into that loop, so the settle spec has to be incapable of one.
// A critically damped spring is not enough: released at its target with leftover velocity it still
// makes a single excursion, sized about v/(omega*e) — proportional to release velocity. It measures
// as zero under a synthesised flick and still lets a real finger start the loop. A tween carries no
// velocity into the animation and cannot pass its target at any flick strength, which is why this
// is a tween and not a spring. Top edge deviation from rest, 1400 px flick in 100 ms, 60 fps:
//
//     spring(0.9, 700)   Material default        102 px    loop reproduces
//     spring(1.0, 700)   critically damped        40 px    loop reproduces
//     spring(1.0, 3800)  FastEffects, stiff        0 px    loop still reproduces by hand
//     tween(300)         no velocity carry-over    0 px    loop cannot be started
//
// Verified by hand on a Galaxy S20 (Android 13) in landscape, on the settings import sheet and the
// account-added sheet. Ruled out first, each reproducing the loop on its own: our settle patch,
// the 85% height cap, skipPartiallyExpanded, a 60% cap that keeps the sheet off the top edge, the
// Haze blur, and GlassScaffold as a whole (replaced by a plain Scaffold). Nothing of ours is in the
// loop — it is a Material3 bug, and this spec only denies it an entry point.
private val settleSpec: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
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
        "an upward flick and can get stuck oscillating in landscape (${failure.detail}). Either Material3 " +
        "changed the field or the keep rules for it in proguard-rules.pro no longer match."
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
