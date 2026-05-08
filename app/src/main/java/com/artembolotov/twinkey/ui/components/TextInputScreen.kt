package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun TextInputScreen(
    label: String,
    initialValue: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    doneLabel: String,
    cancelLabel: String,
    allowBlankDone: Boolean = false,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(cancelLabel)
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onDone(value) },
                enabled = allowBlankDone || value.isNotBlank()
            ) {
                Text(
                    text = doneLabel,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        TextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onDone(value) }
            )
        )
    }
}
