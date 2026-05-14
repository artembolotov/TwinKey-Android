package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.artembolotov.twinkey.ui.theme.AppTextFieldShape
import com.artembolotov.twinkey.ui.theme.appOutlinedTextFieldColors

internal fun Modifier.onTap(onClick: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial).also { it.consume() }
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            if (up != null) { up.consume(); onClick() }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadOnlyField(
    value: String,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        singleLine = true,
        readOnly = true,
        isError = isError,
        supportingText = supportingText,
        shape = AppTextFieldShape,
        colors = appOutlinedTextFieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .onTap(onTap)
    )
}
