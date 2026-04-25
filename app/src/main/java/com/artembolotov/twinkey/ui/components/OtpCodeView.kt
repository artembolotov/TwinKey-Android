package com.artembolotov.twinkey.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artembolotov.twinkey.ui.theme.OtpExpiredDark
import com.artembolotov.twinkey.ui.theme.OtpExpiredLight
import com.artembolotov.twinkey.ui.theme.OtpExpiresDark
import com.artembolotov.twinkey.ui.theme.OtpExpiresLight
import com.artembolotov.twinkey.ui.theme.OtpNormalDark
import com.artembolotov.twinkey.ui.theme.OtpNormalLight

/**
 * Порт OTPCodeView.swift.
 *
 * Цвет: >5 сек зелёный, 3–5 оранжевый, 0–3 красный.
 * Нажатие: копирует код + haptic feedback.
 */
@Composable
fun OtpCodeView(
    code: String,
    secondsRemaining: Int,
    modifier: Modifier = Modifier,
    onTap: ((String) -> Unit)? = null
) {
    val color by animateColorAsState(
        targetValue = otpColor(secondsRemaining, isSystemInDarkTheme()),
        animationSpec = tween(durationMillis = 500),
        label = "otp_color"
    )

    val haptic = LocalHapticFeedback.current

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.then(
            if (onTap != null) {
                Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTap(code)
                }
            } else Modifier
        )
    ) {
        Text(
            text = code.formatOtpCode(),
            fontSize = 42.sp,
            fontWeight = FontWeight.Normal,
            color = color
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "$secondsRemaining",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
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
