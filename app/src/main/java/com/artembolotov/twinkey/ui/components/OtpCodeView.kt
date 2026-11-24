package com.artembolotov.twinkey.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.ui.theme.OtpExpiredDark
import com.artembolotov.twinkey.ui.theme.OtpExpiredLight
import com.artembolotov.twinkey.ui.theme.OtpExpiresDark
import com.artembolotov.twinkey.ui.theme.OtpExpiresLight
import com.artembolotov.twinkey.ui.theme.OtpNormalDark
import com.artembolotov.twinkey.ui.theme.OtpNormalLight

/** How many seconds before the code changes the next one is shown. Same as the iOS app. */
const val NextCodeLeadSeconds = 5

private val CodeFontSize = 38.sp

// The counter and the chip. Same layout as the iOS app (OTPCodeView.swift), scaled to the code's size.
private val SmallFontSize = 12.sp
private val ChipFontSize = 15.sp
// They grow with the font scale up to 130% (iOS: up to the XXL text size). Past it the chip no
// longer fits within the height of the code's digits.
private const val MaxFontScale = 1.3f
// The counter is centred in a fixed width, so it keeps its place as the number of digits changes.
private val CounterWidth = 30.dp
private val RingSize = 18.dp
private val RingStroke = 2.dp
private val ChipPaddingStart = 4.dp
private val ChipPaddingEnd = 10.dp
private val ChipPaddingVertical = 4.dp
private val ChipSpacing = 6.dp
private const val RingTrackAlpha = 0.25f
/** Soft “whisper” capsule behind ring + next digits (secondary at this opacity). Same as iOS. */
private const val CapsuleFillAlpha = 0.10f
private const val AppearScale = 0.9f
private val ExitOvershoot = 40.dp
// The code's tail under the chip fades to this alpha, over FadeLength that ends FadeGap
// before the chip or the counter, whichever is wider.
private const val FadedAlpha = 0.12f
private val FadeLength = 16.dp
private val FadeGap = 10.dp

private val AppearSpec = tween<Float>(durationMillis = 300)
private val SwapSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 400f)
// The ring runs down over the second that has begun, so it is empty at the change.
private val RingSpec = tween<Float>(durationMillis = 1000, easing = LinearEasing)

/**
 * Порт OTPCodeView.swift.
 *
 * Цвет: >5 сек зелёный, 3–5 оранжевый, 0–3 красный.
 * Нажатие: копирует код + haptic feedback.
 *
 * With [nextCode] the code that follows is shown for the last [NextCodeLeadSeconds] seconds: the
 * trailing counter is removed at once and a chip takes its place — a countdown ring (digit already
 * inside it) in the code's urgency colour and the next code's digits in secondary (muted preview,
 * not active blue). The chip fades/scales in in place — no slide across the next digits, and no
 * fading footnote counter painted over them. Drawn over the row so nothing in its layout moves.
 * On a narrow row with a long code it lies over the code's tail, which fades under it. At the
 * change the ring and its digit vanish at once; the next digits travel into the code's place and
 * grow into it without fading, the old code leaves to the left and the counter returns.
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
    val reduceMotion = rememberReduceMotion()
    val fontScale = minOf(density.fontScale, MaxFontScale)
    val smallFontSize = with(density) { (SmallFontSize.value * fontScale).dp.toSp() }
    val chipFontSize = with(density) { (ChipFontSize.value * fontScale).dp.toSp() }
    val ringSize = RingSize * fontScale
    val digitsStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

    // The next code outlives nextCode by the length of the swap: at the change the next code
    // becomes the current one, and the swap starts from where the next code was.
    val appear = remember { Animatable(0f) }
    val swap = remember { Animatable(1f) }
    var shownNext by remember { mutableStateOf<String?>(null) }
    var outgoing by remember { mutableStateOf<String?>(null) }
    var lastCode by remember { mutableStateOf(code) }

    LaunchedEffect(code, nextCode, reduceMotion) {
        val previous = lastCode
        lastCode = code
        if (code != previous && code == shownNext && appear.value > 0f && !reduceMotion) {
            // Ring + digit leave with the preview; the digits continue as the incoming code.
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
            when {
                nextCode == null -> appear.snapTo(0f)
                reduceMotion -> appear.snapTo(1f)
                else -> appear.animateTo(1f, AppearSpec)
            }
        }
    }

    // The ring starts where it stands when the chip appears and then runs down each second.
    val ring = remember { Animatable(0f) }
    var ringShown by remember { mutableStateOf(false) }
    val showsChip = shownNext != null
    LaunchedEffect(secondsRemaining, showsChip, reduceMotion) {
        if (!showsChip) {
            ringShown = false
            return@LaunchedEffect
        }
        // With reduced motion the ring steps once a second; otherwise it runs down over the second.
        val target = if (reduceMotion) {
            secondsRemaining / NextCodeLeadSeconds.toFloat()
        } else {
            (secondsRemaining - 1).coerceAtLeast(0) / NextCodeLeadSeconds.toFloat()
        }
        if (ringShown && !reduceMotion) {
            ring.animateTo(target, RingSpec)
        } else {
            ring.snapTo(target)
            ringShown = true
        }
    }

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var codeTop by remember { mutableFloatStateOf(0f) }
    var codeLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var counterLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var chipWidth by remember { mutableIntStateOf(0) }
    var digitsLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val swapping = outgoing != null
    val p = if (swapping) swap.value else 1f

    val g = with(density) {
        val codeBaseline = codeTop + (codeLayout?.firstBaseline ?: 0f)
        // The chip is centred on the row's height and ends at its end; the digits are centred in it.
        val digitsWidth = digitsLayout?.size?.width ?: 0
        val digitsHeight = digitsLayout?.size?.height ?: 0
        val digitsBaseline = (rootSize.height - digitsHeight) / 2f + (digitsLayout?.firstBaseline ?: 0f)
        val counterWidth = counterLayout?.size?.width ?: 0
        NextCodeGeometry(
            codeBaseline = codeBaseline,
            digitsX = rootSize.width - ChipPaddingEnd.toPx() - digitsWidth,
            digitsShiftY = digitsBaseline - codeBaseline,
            scale = chipFontSize.toPx() / CodeFontSize.toPx(),
            fadeCut = rootSize.width - maxOf(chipWidth, counterWidth) - FadeGap.toPx(),
            fadeLength = FadeLength.toPx(),
            exitOvershoot = ExitOvershoot.toPx()
        )
    }

    Box(modifier = modifier.onSizeChanged { rootSize = it }) {
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
                        style = digitsStyle,
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
                    // The digits come in the colour they had in the preview, which is the fresh code's.
                    color = if (swapping) otpColor(secondsRemaining, isDark) else color,
                    style = digitsStyle,
                    onTextLayout = { codeLayout = it },
                    modifier = if (!swapping) {
                        Modifier.tailFade(if (shownNext != null) appear.value else 0f, g)
                    } else {
                        // Stays opaque so the predicted digits do not blink out before they travel.
                        Modifier.graphicsLayer {
                            // Grows from the next code's digits into its own place, around its baseline.
                            val scale = g.scale + (1f - g.scale) * p
                            scaleX = scale
                            scaleY = scale
                            if (size.height > 0f) {
                                transformOrigin = TransformOrigin(0f, (g.codeBaseline - codeTop) / size.height)
                            }
                            translationX = g.digitsX * (1f - p)
                            translationY = g.digitsShiftY * (1f - p)
                        }
                    }
                )
            }

            if (fullWidth) Spacer(modifier.weight(1f)) else Spacer(Modifier.width(8.dp))

            Text(
                text = "$secondsRemaining",
                fontSize = smallFontSize,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                onTextLayout = { counterLayout = it },
                modifier = Modifier.width(CounterWidth).graphicsLayer {
                    // Lead chip already shows the digit in-ring; hide the footnote counter at once
                    // so it never fades over next digits (double counter). Outside lead it follows
                    // swap (returns during the period-change swap). Same as iOS CounterFade.
                    alpha = if (shownNext != null) 0f else p
                }
            )
        }

        // Ring + digit leave with this preview; the digits continue as the incoming code.
        val next = shownNext
        if (next != null) {
            Box(modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                NextCodeChip(
                    code = next,
                    secondsRemaining = secondsRemaining,
                    progress = { ring.value },
                    color = color,
                    fontSize = chipFontSize,
                    ringSize = ringSize,
                    description = stringResource(R.string.accounts_next_code_description, next.formatOtpCode()),
                    onClick = onTapNext?.let { tap ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tap(next)
                        }
                    },
                    onDigitsLayout = { digitsLayout = it },
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = appear.value
                            val scale = AppearScale + (1f - AppearScale) * appear.value
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                        .onSizeChanged { chipWidth = it.width }
                )
            }
        }
    }
}

/** True when system animator duration scale is 0 — same idea as iOS Reduce Motion. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

private class NextCodeGeometry(
    val codeBaseline: Float,
    /** Where the next code's digits start, from the row's leading edge. */
    val digitsX: Float,
    /** From the code's baseline to the next code's digits' baseline. */
    val digitsShiftY: Float,
    /** Size of the next code's digits relative to the code's. */
    val scale: Float,
    /** Where, from the code's leading edge, its tail has faded. */
    val fadeCut: Float,
    val fadeLength: Float,
    val exitOvershoot: Float
)

/** Fades the code's tail from [NextCodeGeometry.fadeCut], so the chip over it reads on a clear ground. */
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

/**
 * Ring + next digits in a soft “whisper” capsule: digits stay secondary so they read as a preview;
 * the ring keeps the urgency colour of the current period. The seconds digit is drawn already
 * inside the ring — lead appear is a whole-chip fade/scale, not a travel morph.
 */
@Composable
private fun NextCodeChip(
    code: String,
    secondsRemaining: Int,
    progress: () -> Float,
    color: Color,
    fontSize: TextUnit,
    ringSize: Dp,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    onDigitsLayout: (TextLayoutResult) -> Unit = {}
) {
    val numberSize = with(LocalDensity.current) { (ringSize / 2).toSp() }
    val digitsStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    // Same secondary family as the next digits; stadium shape matches iOS Capsule.
    val capsuleFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CapsuleFillAlpha)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { if (description != null) contentDescription = description }
            .background(capsuleFill, RoundedCornerShape(percent = 50))
            .padding(
                start = ChipPaddingStart,
                end = ChipPaddingEnd,
                top = ChipPaddingVertical,
                bottom = ChipPaddingVertical
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ringSize)
                .drawBehind {
                    val stroke = RingStroke.toPx()
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Color.Gray.copy(alpha = RingTrackAlpha),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke)
                    )
                    val sweep = 360f * progress()
                    if (sweep > 0f) {
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
        ) {
            Text(
                text = "$secondsRemaining",
                fontSize = numberSize,
                lineHeight = numberSize,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false,
                style = digitsStyle
            )
        }

        Text(
            text = code.formatOtpCode(),
            fontSize = fontSize,
            lineHeight = fontSize * 1.3f,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            style = digitsStyle,
            onTextLayout = onDigitsLayout
        )
    }
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
