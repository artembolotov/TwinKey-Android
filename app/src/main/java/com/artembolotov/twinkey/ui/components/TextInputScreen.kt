package com.artembolotov.twinkey.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    var value by remember {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(label) },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = cancelLabel)
                }
            },
            actions = {
                TextButton(
                    onClick = { onDone(value.text) },
                    enabled = allowBlankDone || value.text.isNotBlank()
                ) {
                    Text(doneLabel)
                }
            }
        )

        TextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester),
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(value.text) })
        )
    }
}
