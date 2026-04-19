package com.artembolotov.twinkey.ui.settings

import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.Token
import kotlinx.coroutines.launch

/**
 * Порт SettingsScreen.swift.
 *
 * Секции:
 *  - Accounts: Export, Import
 *  - Data: Delete All
 *  - Info: Website, Terms, Privacy, Version
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accounts: List<Token>,
    onImportAccounts: (List<Token>) -> Unit,
    onDeleteAll: () -> Unit,
    onEraseAll: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    onEditAccounts: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { SettingsState() }
    val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(16.dp))

        // Секция: Accounts
        SectionLabel(stringResource(R.string.settings_section_accounts))

        if (onEditAccounts != null) {
            SettingsRow(
                title = stringResource(R.string.settings_edit_accounts),
                enabled = accounts.isNotEmpty(),
                onClick = { onEditAccounts() }
            )
        }

        SettingsRow(
            title = stringResource(R.string.settings_export),
            enabled = accounts.isNotEmpty(),
            onClick = { state.showExport = true }
        )

        SettingsRow(
            title = stringResource(R.string.settings_import),
            onClick = { state.showImport = true }
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Секция: Data Management
        SectionLabel(stringResource(R.string.settings_section_data))

        SettingsRow(
            title = stringResource(R.string.settings_delete_all),
            enabled = accounts.isNotEmpty(),
            destructive = true,
            onClick = { state.showDeleteAll = true }
        )

        SettingsRow(
            title = stringResource(R.string.settings_erase_all),
            destructive = true,
            onClick = { state.showEraseAll = true }
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Секция: Info
        SectionLabel(stringResource(R.string.settings_section_info))

        SettingsRow(
            title = stringResource(R.string.settings_website),
            isLink = true,
            onClick = { openUrl(context, "https://twinkey.app") }
        )

        SettingsRow(
            title = stringResource(R.string.settings_terms),
            isLink = true,
            onClick = { openUrl(context, "https://twinkey.app/terms") }
        )

        SettingsRow(
            title = stringResource(R.string.settings_privacy),
            isLink = true,
            onClick = { openUrl(context, "https://twinkey.app/privacy") }
        )

        val version = remember {
            try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                "${pi.versionName} (${PackageInfoCompat.getLongVersionCode(pi)})"
            } catch (_: Exception) { "—" }
        }
        VersionRow(label = stringResource(R.string.settings_version), version = version)

        Spacer(Modifier.height(24.dp))

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_done))
        }

        Spacer(Modifier.height(8.dp))
    }

    // Диалог: удалить все
    if (state.showDeleteAll) {
        AlertDialog(
            onDismissRequest = { state.showDeleteAll = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.showDeleteAll = false
                        onDeleteAll()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDeleteAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Диалог: стереть всё
    if (state.showEraseAll) {
        AlertDialog(
            onDismissRequest = { state.showEraseAll = false },
            title = { Text(stringResource(R.string.settings_erase_all_title)) },
            text = { Text(stringResource(R.string.settings_erase_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    state.showEraseAll = false
                    onEraseAll()
                }) { Text(stringResource(R.string.settings_erase_all_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showEraseAll = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // BottomSheet: Export
    if (state.showExport) {
        ModalBottomSheet(
            onDismissRequest = { state.showExport = false },
            sheetState = exportSheetState
        ) {
            AccountsExportScreen(
                accounts = accounts,
                onSuccess = {
                    scope.launch {
                        exportSheetState.hide()
                        state.showExport = false
                        onMessage(exportSuccess)
                        onDismiss()
                    }
                },
                onError = { msg ->
                    scope.launch {
                        exportSheetState.hide()
                        state.showExport = false
                        onMessage(msg)
                    }
                },
                onDismiss = {
                    scope.launch {
                        exportSheetState.hide()
                        state.showExport = false
                    }
                }
            )
        }
    }

    // BottomSheet: Import
    if (state.showImport) {
        ModalBottomSheet(
            onDismissRequest = { state.showImport = false },
            sheetState = importSheetState
        ) {
            AccountsImportScreen(
                onImport = { tokens ->
                    scope.launch {
                        importSheetState.hide()
                        state.showImport = false
                        onImportAccounts(tokens)
                        onMessage(importSuccess)
                        onDismiss()
                    }
                },
                onError = { msg ->
                    scope.launch {
                        importSheetState.hide()
                        state.showImport = false
                        onMessage(msg)
                    }
                },
                onDismiss = {
                    scope.launch {
                        importSheetState.hide()
                        state.showImport = false
                    }
                }
            )
        }
    }
}

@Composable
private fun VersionRow(label: String, version: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label $version",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    detail: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    isLink: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLink) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.NorthEast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    HorizontalDivider()
}

private class SettingsState {
    var showDeleteAll by mutableStateOf(false)
    var showEraseAll by mutableStateOf(false)
    var showExport by mutableStateOf(false)
    var showImport by mutableStateOf(false)
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
