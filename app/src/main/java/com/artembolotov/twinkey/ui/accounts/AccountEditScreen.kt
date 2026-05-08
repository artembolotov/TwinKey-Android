package com.artembolotov.twinkey.ui.accounts

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.TextInputScreen
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base32

private enum class AccountEditField { Issuer, Name }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    token: Token,
    onDone: (Token) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit
) {
    val state = remember { AccountEditState(token.issuer, token.name) }

    val hasChanges = state.issuer != token.issuer || state.name != token.name
    val canDone = state.issuer.isNotBlank() && hasChanges

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val secretBase32 = remember {
        Base32().encodeToString(token.generator.secret).trimEnd('=')
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_title)) },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { onDone(token.copy(issuer = state.issuer.trim(), name = state.name.trim())) },
                            enabled = canDone
                        ) {
                            Text(stringResource(R.string.edit_done))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = state.issuer,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.edit_issuer)) },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial).also { it.consume() }
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (up != null) { up.consume(); state.activeField = AccountEditField.Issuer }
                            }
                        }
                )

                TextField(
                    value = state.name,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.edit_account)) },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial).also { it.consume() }
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (up != null) { up.consume(); state.activeField = AccountEditField.Name }
                            }
                        }
                )

                HorizontalDivider()

                ReadOnlyRow(
                    label = stringResource(R.string.edit_secret),
                    value = secretBase32,
                    trailing = {
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", secretBase32)))
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.edit_copy_secret))
                        }
                    }
                )

                ReadOnlyRow(label = stringResource(R.string.edit_digits), value = token.generator.digits.toString())
                ReadOnlyRow(label = stringResource(R.string.edit_period), value = stringResource(R.string.edit_period_seconds, token.generator.periodOrDefault))
                ReadOnlyRow(label = stringResource(R.string.edit_algorithm), value = token.generator.algorithm.name)

                HorizontalDivider()

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { state.showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.edit_delete))
                }
            }
        }

        val currentField = state.activeField
        if (currentField != null) {
            BackHandler { state.activeField = null }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (currentField) {
                    AccountEditField.Issuer -> TextInputScreen(
                        label = stringResource(R.string.edit_issuer),
                        initialValue = state.issuer,
                        placeholder = "",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        doneLabel = stringResource(R.string.edit_done),
                        cancelLabel = stringResource(R.string.cancel),
                        onDone = { value -> state.issuer = value; state.activeField = null },
                        onCancel = { state.activeField = null }
                    )
                    AccountEditField.Name -> TextInputScreen(
                        label = stringResource(R.string.edit_account),
                        initialValue = state.name,
                        placeholder = "",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        doneLabel = stringResource(R.string.edit_done),
                        cancelLabel = stringResource(R.string.cancel),
                        allowBlankDone = true,
                        onDone = { value -> state.name = value; state.activeField = null },
                        onCancel = { state.activeField = null }
                    )
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { state.showDeleteDialog = false },
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_message, token.issuer.ifEmpty { token.name })) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(token.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.edit_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private class AccountEditState(issuer: String, name: String) {
    var issuer by mutableStateOf(issuer)
    var name by mutableStateOf(name)
    var showDeleteDialog by mutableStateOf(false)
    var activeField: AccountEditField? by mutableStateOf(null)
}

@Composable
private fun ReadOnlyRow(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        trailing?.invoke()
    }
}
