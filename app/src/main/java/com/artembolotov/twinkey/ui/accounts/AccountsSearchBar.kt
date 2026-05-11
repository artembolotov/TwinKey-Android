package com.artembolotov.twinkey.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.ui.theme.CardBackgroundDark
import com.artembolotov.twinkey.ui.theme.CardBackgroundLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSearchBar(
    query: String,
    searchActive: Boolean,
    editMode: Boolean,
    isLandscape: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onClearQuery: () -> Unit,
    onDoneClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLandscape && !editMode) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (isLandscape && !editMode) 4.dp else 16.dp,
                    end = if (isLandscape) 8.dp else 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                )
                .onFocusChanged { onSearchActiveChange(it.isFocused) },
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = query,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = remember { MutableInteractionSource() },
                    placeholder = { Text(stringResource(R.string.accounts_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = searchActive,
                            enter = fadeIn() + scaleIn(initialScale = 0.7f),
                            exit = fadeOut() + scaleOut(targetScale = 0.7f)
                        ) {
                            IconButton(onClick = {
                                onClearQuery()
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.accounts_search_clear))
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isSystemInDarkTheme()) CardBackgroundDark else CardBackgroundLight,
                        unfocusedContainerColor = if (isSystemInDarkTheme()) CardBackgroundDark else CardBackgroundLight,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
                )
            }
        )
        if (isLandscape) {
            if (editMode) {
                TextButton(
                    onClick = onDoneClick,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.accounts_done), fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add_account))
                }
            }
        }
    }
}
