package com.artembolotov.twinkey.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.ui.theme.OtpExpiredDark
import com.artembolotov.twinkey.ui.theme.OtpExpiredLight
import com.artembolotov.twinkey.ui.theme.OtpExpiresDark
import com.artembolotov.twinkey.ui.theme.OtpExpiresLight
import com.artembolotov.twinkey.ui.theme.OtpNormalDark
import com.artembolotov.twinkey.ui.theme.OtpNormalLight
import kotlin.math.roundToInt

/** How many seconds before the code changes the next one is shown. Same as the iOS app. */
const val NextCodeLeadSeconds = 5

private val CodeFontSize = 38.sp

// The counter and the next code. Same values as the iOS app (OTPCodeView.swift).
private val SmallFontSize = 12.sp
// They grow with the font scale up to 130% (iOS: up to the XXL text size). Past it the two no
// longer fit one above the other within the height of the code's digits.
private val MaxSmallFontSize = (SmallFontSize.value * 1.3f).dp
private val AppearSlide = 10.dp
private val ExitOvershoot = 40.dp
// The code's tail under the next code fades to this alpha, over FadeLength that ends FadeGap
// before the next code or the counter, whichever is wider.
private const val FadedAlpha = 0.12f
private val FadeLength = 16.dp
private val FadeGap = 10.dp

// Cap height of the system font as a share of its size: the counter's cap line is set on the code's.
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
 * counter moves up to the code's cap line and the next code appears under it, on the code's
 * baseline, both drawn over the row so nothing in its layout moves. On a narrow row with a long
 * code the next code lies over the code's tail, which fades under it. At the change the next
 * code's digits travel into the code's place and grow into it, the old code leaves to the left and
 * the counter returns.
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
    val smallFontSize = with(density) {
        if (SmallFontSize.toDp() > MaxSmallFontSize) MaxSmallFontSize.toSp() else SmallFontSize
    }

    // The next code outlives nextCode by the length of the swap: at the change the next code
    // becomes the current one, and the swap starts from where the next code was.
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
    var nextWidth by remember { mutableIntStateOf(0) }
    var nextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val swapping = outgoing != null
    val p = if (swapping) swap.value else 1f
    val labelColor = MaterialTheme.colorScheme.onSurface

    val g = with(density) {
        val codeBaseline = codeTop + (codeLayout?.firstBaseline ?: 0f)
        val codeCapTop = codeBaseline - CapHeight * CodeFontSize.toPx()
        val counterBaseline = counterTop + (counterLayout?.firstBaseline ?: 0f)
        val nextX = rootWidth - nextWidth.toFloat()
        val nextBaseline = nextLayout?.firstBaseline ?: 0f
        val digitsInNextX = nextLayout?.getHorizontalPosition(NextCodeDigitsStart, usePrimaryDirection = true) ?: 0f
        val counterWidth = counterLayout?.size?.width ?: 0
        NextCodeGeometry(
            codeBaseline = codeBaseline,
            nextX = nextX,
            nextTop = codeBaseline - nextBaseline,
            nextBaseline = nextBaseline,
            digitsInNextX = digitsInNextX,
            digitsX = nextX + digitsInNextX,
            scale = smallFontSize.toPx() / CodeFontSize.toPx(),
            counterShiftY = codeCapTop + CapHeight * smallFontSize.toPx() - counterBaseline,
            fadeCut = rootWidth - maxOf(nextWidth, counterWidth) - FadeGap.toPx(),
            fadeLength = FadeLength.toPx(),
            appearSlide = AppearSlide.toPx(),
            exitOvershoot = ExitOvershoot.toPx()
        )
    }

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
            Box(modifier = Modifier.onGloballyPositioned { codeTop = it.positionInParent().y }) {
                outgoing?.let { old ->
                    Text(
                        text = old.formatOtpCode(),
                        fontSize = CodeFontSize,
                        fontWeight = FontWeight.Normal,
                        color = otpColor(0, isDark),
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = -((codeLayout?.size?.width ?: 0) + g.exitOvershoot) * p
                                alpha = 1f - 0.9f * p
                            }
                            .tailFade(1f, g)
                    )
                }
                Text(
                    text = code.formatOtpCode(),
                    fontSize = CodeFontSize,
                    fontWeight = FontWeight.Normal,
                    color = if (swapping) lerp(labelColor, color, p) else color,
                    onTextLayout = { codeLayout = it },
                    modifier = if (!swapping) {
                        Modifier.tailFade(if (shownNext != null) appear.value else 0f, g)
                    } else {
                        Modifier.graphicsLayer {
                            // Grows from the next code's digits into its own place, around its baseline.
                            val scale = g.scale + (1f - g.scale) * p
                            scaleX = scale
                            scaleY = scale
                            if (size.height > 0f) {
                                transformOrigin = TransformOrigin(0f, (g.codeBaseline - codeTop) / size.height)
                            }
                            translationX = g.digitsX * (1f - p)
                            alpha = (p * 2f).coerceAtMost(1f)
                        }
                    }
                )
            }

            if (fullWidth) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(8.dp))

            Text(
                text = "$secondsRemaining",
                fontSize = smallFontSize,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                onTextLayout = { counterLayout = it },
                modifier = Modifier
                    .onGloballyPositioned { counterTop = it.positionInParent().y }
                    .graphicsLayer {
                        val t = if (swapping) 1f - p else appear.value
                        translationY = g.counterShiftY * t
                    }
            )
        }

        if (shownNext != null || swapping) {
            Box(Modifier.matchParentSize()) {
                val next = shownNext
                if (swapping || next == null) {
                    // The arrow rides along with the digits as they grow into the code, and fades out early.
                    NextCodeLabel(
                        code = code,
                        fontSize = smallFontSize,
                        showsDigits = false,
                        modifier = Modifier
                            .offset { IntOffset(g.nextX.roundToInt(), g.nextTop.roundToInt()) }
                            .graphicsLayer {
                                val scale = 1f + (1f / g.scale - 1f) * p
                                scaleX = scale
                                scaleY = scale
                                if (size.width > 0f && size.height > 0f) {
                                    transformOrigin = TransformOrigin(g.digitsInNextX / size.width, g.nextBaseline / size.height)
                                }
                                translationX = -g.digitsX * p
                                alpha = 1f - (p * 3f).coerceAtMost(1f)
                            }
                    )
                } else {
                    val description = stringResource(R.string.accounts_next_code_description, next.formatOtpCode())
                    NextCodeLabel(
                        code = next,
                        fontSize = smallFontSize,
                        onTextLayout = { nextLayout = it },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (g.nextX + g.appearSlide * (1f - appear.value)).roundToInt(),
                                    g.nextTop.roundToInt()
                                )
                            }
                            .graphicsLayer { alpha = appear.value }
                            .onSizeChanged { nextWidth = it.width }
                            .then(
                                if (onTapNext != null) {
                                    Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTapNext(next)
                                    }
                                } else Modifier
                            )
                            .semantics { contentDescription = description }
                    )
                }
            }
        }
    }
}

private class NextCodeGeometry(
    val codeBaseline: Float,
    val nextX: Float,
    val nextTop: Float,
    val nextBaseline: Float,
    /** Where the next code's digits start, from the next code's leading edge. */
    val digitsInNextX: Float,
    /** Where the next code's digits start, from the row's leading edge. */
    val digitsX: Float,
    /** Size of the next code's digits relative to the code's. */
    val scale: Float,
    val counterShiftY: Float,
    /** Where, from the code's leading edge, its tail has faded. */
    val fadeCut: Float,
    val fadeLength: Float,
    val appearSlide: Float,
    val exitOvershoot: Float
)

/** Fades the code's tail from [NextCodeGeometry.fadeCut], so the next code over it reads on a clear ground. */
private fun Modifier.tailFade(amount: Float, g: NextCodeGeometry): Modifier =
    if (amount <= 0f) this else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (g.fadeCut < size.width) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - (1f - FadedAlpha) * amount)),
                        startX = g.fadeCut - g.fadeLength,
                        endX = g.fadeCut
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }

// The label is "→ " followed by the digits.
private const val NextCodeDigitsStart = 2

/** An arrow in the counter's grey and the next code's digits in the label colour. */
@Composable
private fun NextCodeLabel(
    code: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    showsDigits: Boolean = true,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 0.85.em)) {
                append("→ ")
            }
            withStyle(
                SpanStyle(
                    color = if (showsDigits) labelColor else Color.Transparent,
                    fontWeight = FontWeight.SemiBold
                )
            ) { append(code.formatOtpCode()) }
        },
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        onTextLayout = onTextLayout,
        modifier = modifier
    )
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
