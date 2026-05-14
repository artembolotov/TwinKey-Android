package com.artembolotov.twinkey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppTextFieldShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTextFieldColors(): TextFieldColors {
    val bg = if (isSystemInDarkTheme()) SearchFieldBackgroundDark else SearchFieldBackgroundLight
    return TextFieldDefaults.colors(
        focusedContainerColor   = bg,
        unfocusedContainerColor = bg,
        disabledContainerColor  = bg,
        errorContainerColor     = bg,
        focusedIndicatorColor   = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor  = Color.Transparent,
        errorIndicatorColor     = Color.Transparent,
    )
}

/** Colors for OutlinedTextField — indicator slots drive the outline color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appOutlinedTextFieldColors(): TextFieldColors {
    val bg = if (isSystemInDarkTheme()) SearchFieldBackgroundDark else SearchFieldBackgroundLight
    return TextFieldDefaults.colors(
        focusedContainerColor   = bg,
        unfocusedContainerColor = bg,
        disabledContainerColor  = bg,
        errorContainerColor     = bg,
        focusedIndicatorColor   = MaterialTheme.colorScheme.outlineVariant,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
        disabledIndicatorColor  = MaterialTheme.colorScheme.outlineVariant,
        errorIndicatorColor     = MaterialTheme.colorScheme.error,
    )
}
