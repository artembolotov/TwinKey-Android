package com.artembolotov.twinkey.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.ui.theme.CardBackgroundDark
import com.artembolotov.twinkey.ui.theme.CardBackgroundLight
import com.artembolotov.twinkey.ui.theme.NextCodeDark
import com.artembolotov.twinkey.ui.theme.NextCodeLight
import com.artembolotov.twinkey.ui.theme.OtpExpiredDark
import com.artembolotov.twinkey.ui.theme.OtpExpiredLight
import com.artembolotov.twinkey.ui.theme.OtpExpiresDark
import com.artembolotov.twinkey.ui.theme.OtpExpiresLight
import com.artembolotov.twinkey.ui.theme.OtpNormalDark
import com.artembolotov.twinkey.ui.theme.OtpNormalLight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

/** How many seconds before the code changes the next one is shown. Same as the iOS app. */
const val NextCodeLeadSeconds = 5

private val CodeFontSize = 38.sp
private val CounterFontSize = 12.sp

// The next-code capsule. Same values as the iOS app (OTPCodeView.swift).
private val NextCodeFontSize = 12.sp
private val CapsuleHeight = 20.dp
private val CapsulePadding = 7.dp
private val CapsuleIconSize = 10.dp
private val CapsuleIconGap = 4.dp
private val StackGap = 6.dp
private val AppearSlide = 10.dp
private val ExitOvershoot = 40.dp
private const val NextCodeScale = 12f / 38f

// Cap height of the system font as a share of its size. The counter and the capsule are centred
// together on the code's digits, not on its line box.
private const val CapHeight = 0.71f

private val AppearSpec = tween<Float>(durationMillis = 300)
private val SwapSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 400f)

/**
 * Порт OTPCodeView.swift.
 *
 * Цвет: >5 сек зелёный, 3–5 оранжевый, 0–3 красный.
 * Нажатие: копирует код + haptic feedback.
 *
 * With [nextCode] the code that follows is shown for the last [NextCodeLeadSeconds] seconds: the
 * counter moves up and a capsule with the next code appears under it, both drawn over the row so
 * nothing in its layout moves. On a narrow row with a long code the capsule lies over the code's
 * tail, which shows through its frosted glass. At the change the capsule's digits travel into
 * the code's place and grow into it, the old code leaves to the left and the counter returns.
 */
@Composable
fun OtpCodeView(
    code: String,
    secondsRemaining: Int,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
    nextCode: String? = null,
    onTap: ((String) -> Unit)? = null,
    onTapNext: ((String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val color by animateColorAsState(
        targetValue = otpColor(secondsRemaining, isDark),
        animationSpec = tween(durationMillis = 500),
        label = "otp_color"
    )

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // The capsule outlives nextCode by the length of the swap: at the change the code that was in
    // the capsule becomes the current one, and the swap starts from where the capsule was.
    val appear = remember { Animatable(0f) }
    val swap = remember { Animatable(1f) }
    var shownNext by remember { mutableStateOf<String?>(null) }
    var outgoing by remember { mutableStateOf<String?>(null) }
    var lastCode by remember { mutableStateOf(code) }

    LaunchedEffect(code, nextCode) {
        val previous = lastCode
        lastCode = code
        if (code != previous && code == shownNext && appear.value > 0f) {
            swap.snapTo(0f)
            appear.snapTo(0f)
            shownNext = null
            outgoing = previous
            swap.animateTo(1f, SwapSpec)
            outgoing = null
        } else {
            swap.snapTo(1f)
            outgoing = null
            shownNext = nextCode
            if (nextCode != null) appear.animateTo(1f, AppearSpec) else appear.snapTo(0f)
        }
    }

    var rootWidth by remember { mutableIntStateOf(0) }
    var codeTop by remember { mutableFloatStateOf(0f) }
    var codeLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var counterTop by remember { mutableFloatStateOf(0f) }
    var counterLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var capsuleWidth by remember { mutableIntStateOf(0) }
    var capsuleText by remember { mutableStateOf<TextLayoutResult?>(null) }

    val swapping = outgoing != null
    val p = if (swapping) swap.value else 1f
    val nextColor = if (isDark) NextCodeDark else NextCodeLight

    val g = with(density) {
        val codeBaseline = codeTop + (codeLayout?.firstBaseline ?: 0f)
        val counterCap = CapHeight * CounterFontSize.toPx()
        val digitsCenter = codeBaseline - CapHeight * CodeFontSize.toPx() / 2
        val capsuleHeight = CapsuleHeight.toPx()
        val stackTop = digitsCenter - (counterCap + StackGap.toPx() + capsuleHeight) / 2
        val capsuleTop = stackTop + counterCap + StackGap.toPx()
        val capsuleX = rootWidth - capsuleWidth.toFloat()
        val digitsInCapsuleX = (CapsulePadding + CapsuleIconSize + CapsuleIconGap).toPx()
        val digitsInCapsuleBaseline = capsuleText?.let { (capsuleHeight - it.size.height) / 2 + it.firstBaseline } ?: 0f
        val counterBaseline = counterTop + (counterLayout?.firstBaseline ?: 0f)
        StackGeometry(
            codeBaseline = codeBaseline,
            capsuleX = capsuleX,
            capsuleTop = capsuleTop,
            digitsInCapsuleX = digitsInCapsuleX,
            digitsInCapsuleBaseline = digitsInCapsuleBaseline,
            digitsX = capsuleX + digitsInCapsuleX,
            digitsDy = capsuleTop + digitsInCapsuleBaseline - codeBaseline,
            // The counter's digits end where the capsule's digits end, its cap line tops the stack.
            counterShiftX = -CapsulePadding.toPx(),
            counterShiftY = stackTop + counterCap - counterBaseline,
            appearSlide = AppearSlide.toPx(),
            exitOvershoot = ExitOvershoot.toPx()
        )
    }

    val showCapsule = shownNext != null || swapping
    val codeHaze = remember { HazeState() }

    Box(modifier = modifier.onSizeChanged { rootWidth = it.width }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (onTap != null) {
                Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTap(code)
                }
            } else Modifier
        ) {
            // The capsule blurs what lies under it, so the code is its haze source.
            Box(
                modifier = Modifier
                    .onGloballyPositioned { codeTop = it.positionInParent().y }
                    .then(if (showCapsule) Modifier.hazeSource(codeHaze) else Modifier)
            ) {
                outgoing?.let { old ->
                    Text(
                        text = old.formatOtpCode(),
                        fontSize = CodeFontSize,
                        fontWeight = FontWeight.Normal,
                        color = otpColor(0, isDark),
                        modifier = Modifier.graphicsLayer {
                            translationX = -((codeLayout?.size?.width ?: 0) + g.exitOvershoot) * p
                            alpha = 1f - 0.9f * p
                        }
                    )
                }
                Text(
                    text = code.formatOtpCode(),
                    fontSize = CodeFontSize,
                    fontWeight = FontWeight.Normal,
                    color = if (swapping) lerp(nextColor, color, p) else color,
                    onTextLayout = { codeLayout = it },
                    modifier = if (!swapping) Modifier else Modifier.graphicsLayer {
                        // Grows from the capsule's digits into its own place, around its baseline.
                        val scale = NextCodeScale + (1f - NextCodeScale) * p
                        scaleX = scale
                        scaleY = scale
                        if (size.height > 0f) {
                            transformOrigin = TransformOrigin(0f, (g.codeBaseline - codeTop) / size.height)
                        }
                        translationX = g.digitsX * (1f - p)
                        translationY = g.digitsDy * (1f - p)
                        alpha = (p * 2f).coerceAtMost(1f)
                    }
                )
            }

            if (fullWidth) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(8.dp))

            Text(
                text = "$secondsRemaining",
                fontSize = CounterFontSize,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                onTextLayout = { counterLayout = it },
                modifier = Modifier
                    .onGloballyPositioned { counterTop = it.positionInParent().y }
                    .graphicsLayer {
                        val t = if (swapping) 1f - p else appear.value
                        translationX = g.counterShiftX * t
                        translationY = g.counterShiftY * t
                    }
            )
        }

        if (showCapsule) {
            Box(Modifier.matchParentSize()) {
                val next = shownNext
                if (swapping || next == null) {
                    // Rides along with the digits as they grow into the code, and fades out early.
                    NextCodeCapsule(
                        code = code,
                        background = Modifier.background(glassFallback(isDark)),
                        modifier = Modifier
                            .offset { IntOffset(g.capsuleX.roundToInt(), g.capsuleTop.roundToInt()) }
                            .graphicsLayer {
                                val scale = 1f + (1f / NextCodeScale - 1f) * p
                                scaleX = scale
                                scaleY = scale
                                if (size.width > 0f && size.height > 0f) {
                                    transformOrigin = TransformOrigin(
                                        g.digitsInCapsuleX / size.width,
                                        g.digitsInCapsuleBaseline / size.height
                                    )
                                }
                                translationX = -g.digitsX * p
                                translationY = -g.digitsDy * p
                                alpha = 1f - (p * 2.2f).coerceAtMost(1f)
                            }
                    )
                } else {
                    val description = stringResource(R.string.accounts_next_code_description, next.formatOtpCode())
                    NextCodeCapsule(
                        code = next,
                        background = Modifier.hazeEffect(state = codeHaze, style = glassStyle(isDark)),
                        onTextLayout = { capsuleText = it },
                        onClick = onTapNext?.let { tap ->
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tap(next)
                            }
                        },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (g.capsuleX + g.appearSlide * (1f - appear.value)).roundToInt(),
                                    g.capsuleTop.roundToInt()
                                )
                            }
                            .graphicsLayer { alpha = appear.value }
                            .onSizeChanged { capsuleWidth = it.width }
                            .semantics { contentDescription = description }
                    )
                }
            }
        }
    }
}

private class StackGeometry(
    val codeBaseline: Float,
    val capsuleX: Float,
    val capsuleTop: Float,
    val digitsInCapsuleX: Float,
    val digitsInCapsuleBaseline: Float,
    val digitsX: Float,
    val digitsDy: Float,
    val counterShiftX: Float,
    val counterShiftY: Float,
    val appearSlide: Float,
    val exitOvershoot: Float
)

@Composable
private fun NextCodeCapsule(
    code: String,
    background: Modifier,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) NextCodeDark else NextCodeLight
    val accent = if (isDark) OtpNormalDark else OtpNormalLight
    Row(
        modifier = modifier
            .height(CapsuleHeight)
            .clip(CircleShape)
            .then(background)
            .border(0.5.dp, accent.copy(alpha = if (isDark) 0.30f else 0.22f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = CapsulePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CapsuleIconGap)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(CapsuleIconSize)
        )
        Text(
            text = code.formatOtpCode(),
            fontSize = NextCodeFontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.02.em,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            // The theme's 24sp line would not fit the 20dp capsule.
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum", lineHeight = 16.sp),
            onTextLayout = onTextLayout
        )
    }
}

// Frosted glass: the code under the capsule, blurred, behind a translucent card layer and the accent tone.
private fun glassStyle(isDark: Boolean): HazeStyle {
    val card = if (isDark) CardBackgroundDark else CardBackgroundLight
    val accent = if (isDark) OtpNormalDark else OtpNormalLight
    return HazeStyle(
        backgroundColor = card,
        tints = listOf(
            HazeTint(card.copy(alpha = 0.62f)),
            HazeTint(accent.copy(alpha = if (isDark) 0.20f else 0.12f))
        ),
        blurRadius = 3.dp,
        noiseFactor = 0f,
        // Before Android 12 there is no blur: a denser layer keeps the digits readable over the code.
        fallbackTint = HazeTint(glassFallback(isDark))
    )
}

private fun glassFallback(isDark: Boolean): Color {
    val card = if (isDark) CardBackgroundDark else CardBackgroundLight
    val accent = if (isDark) OtpNormalDark else OtpNormalLight
    return lerp(card, accent, if (isDark) 0.20f else 0.12f).copy(alpha = 0.85f)
}

fun otpColor(secondsRemaining: Int, isDark: Boolean): Color = when {
    secondsRemaining <= 3 -> if (isDark) OtpExpiredDark else OtpExpiredLight
    secondsRemaining <= 5 -> if (isDark) OtpExpiresDark else OtpExpiresLight
    else                  -> if (isDark) OtpNormalDark  else OtpNormalLight
}

fun String.formatOtpCode(): String {
    if (isEmpty()) return this
    val mid = length / 2
    return "${take(mid)} ${drop(mid)}"
}
