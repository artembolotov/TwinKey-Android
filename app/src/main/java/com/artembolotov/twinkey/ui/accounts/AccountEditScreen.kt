package com.artembolotov.twinkey.ui.accounts

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.Token
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base32

/**
 * Порт AccountEditScreen.swift.
 *
 * Редактируемые поля: issuer, name (account).
 * Read-only: secret (с кнопкой копировать), digits, period, algorithm.
 * Кнопка Done активна только при непустом issuer И наличии изменений.
 * Кнопка Delete — деструктивная, с подтверждением.
 */
@Composable
fun AccountEditScreen(
    token: Token,
    onDone: (Token) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit
) {
    var issuer by remember { mutableStateOf(token.issuer) }
    var name by remember { mutableStateOf(token.name) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val hasChanges = issuer != token.issuer || name != token.name
    val canDone = issuer.isNotBlank() && hasChanges

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val secretBase32 = remember {
        Base32().encodeToString(token.generator.secret).trimEnd('=')
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.edit_title),
            style = MaterialTheme.typography.titleLarge
        )

        // Editable: Service name
        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(stringResource(R.string.edit_issuer)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        // Editable: Account (email / username)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.edit_account)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        HorizontalDivider()

        // Read-only: Secret key с кнопкой копирования
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

        // Read-only: Digits
        ReadOnlyRow(
            label = stringResource(R.string.edit_digits),
            value = token.generator.digits.toString()
        )

        // Read-only: Period
        ReadOnlyRow(
            label = stringResource(R.string.edit_period),
            value = stringResource(R.string.edit_period_seconds, token.generator.periodOrDefault)
        )

        // Read-only: Algorithm
        ReadOnlyRow(
            label = stringResource(R.string.edit_algorithm),
            value = token.generator.algorithm.name
        )

        HorizontalDivider()

        // Кнопки Done / Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    onDone(token.copy(issuer = issuer.trim(), name = name.trim()))
                },
                enabled = canDone
            ) {
                Text(stringResource(R.string.edit_done))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Delete — деструктивная
        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(stringResource(R.string.edit_delete))
        }

        Spacer(Modifier.height(8.dp))
    }

    // Диалог подтверждения удаления
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_message, token.issuer.ifEmpty { token.name })) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(token.id) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.edit_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        trailing?.invoke()
    }
}
