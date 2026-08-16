package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.ui.theme.PageBackgroundDark
import com.artembolotov.twinkey.ui.theme.PageBackgroundLight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@Composable
fun GlassScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val pageBackground = if (isSystemInDarkTheme()) PageBackgroundDark else PageBackgroundLight
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = pageBackground.copy(alpha = 0.2f),
        tints = listOf(HazeTint(pageBackground.copy(alpha = 0.1f))),
        blurRadius = 4.dp
    )
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }
    val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val safeLeft = with(density) {
        maxOf(
            WindowInsets.navigationBars.getLeft(this, layoutDirection),
            WindowInsets.displayCutout.getLeft(this, layoutDirection)
        ).toDp()
    }
    val safeRight = with(density) {
        maxOf(
            WindowInsets.navigationBars.getRight(this, layoutDirection),
            WindowInsets.displayCutout.getRight(this, layoutDirection)
        ).toDp()
    }

    Box(modifier = modifier.fillMaxSize().background(pageBackground)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            content(PaddingValues(start = safeLeft, top = topBarHeightDp, end = safeRight, bottom = navBarBottomDp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .hazeEffect(state = hazeState, style = hazeStyle) {
                    mask = Brush.verticalGradient(
                        0.0f to Color.Black,
                        0.75f to Color.Black,
                        1.0f to Color.Transparent
                    )
                }
                .onSizeChanged { topBarHeightPx = it.height }
        ) {
            topBar()
        }
    }
}
