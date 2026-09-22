package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Widest a form or a single column of content gets on an unfolded foldable or a tablet. Matches
// Material3's default sheetMaxWidth, so a screen shown inside a bottom sheet is never narrowed.
val MaxContentWidth = 640.dp

// Centers the element in the available width and caps it at MaxContentWidth. Below the cap it
// behaves like fillMaxWidth, so phones are unaffected.
fun Modifier.centeredContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = MaxContentWidth)
    .fillMaxWidth()
