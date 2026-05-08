package com.artembolotov.twinkey.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.data.ImportResult
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.AppModalBottomSheet
import com.artembolotov.twinkey.ui.components.rememberAppSheetState
import kotlinx.coroutines.launch

private enum class SettingsSubScreen { None, Export, ImportSelection }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accounts: List<Token>,
    onImportAccounts: (List<Token>) -> Unit,
    onDeleteAll: () -> Unit,
    onEraseAll: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    onEditAccounts: (() -> Unit)? = null,
    settingsExportVisible: Boolean = false,
    settingsImportResult: ImportResult? = null,
    onShowExport: () -> Unit = {},
    onHideExport: () -> Unit = {},
    onSetImportResult: (ImportResult?) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showDeleteAll = rememberSaveable { mutableStateOf(false) }
    val showEraseAll = rememberSaveable { mutableStateOf(false) }
    val showImport = rememberSaveable { mutableStateOf(false) }
    val showReportProblem = rememberSaveable { mutableStateOf(false) }
    val importSheetState = rememberAppSheetState()

    val exportSuccess = stringResource(R.string.backup_export_success)
    val importSuccess = stringResource(R.string.backup_import_success)

    val subScreen = when {
        settingsExportVisible -> SettingsSubScreen.Export
        settingsImportResult != null -> SettingsSubScreen.ImportSelection
        else -> SettingsSubScreen.None
    }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = {
            if (targetState == SettingsSubScreen.None)
                fadeIn() togetherWith (slideOutVertically { it } + fadeOut())
            else
                (slideInVertically { it } + fadeIn()) togetherWith fadeOut()
        },
        modifier = Modifier.fillMaxSize(),
        label = "settings_sub_screen"
    ) { screen ->
        when (screen) {
            SettingsSubScreen.Export -> {
                AccountsExportScreen(
                    accounts = accounts,
                    onSuccess = {
                        onHideExport()
                        onMessage(exportSuccess)
                        onDismiss()
                    },
                    onError = { msg ->
                        onHideExport()
                        onMessage(msg)
                    },
                    onDismiss = { onHideExport() }
                )
            }

            SettingsSubScreen.ImportSelection -> {
                // remember сохраняет снимок на время exit-анимации, когда importResult уже null
                val result = remember { settingsImportResult }
                if (result != null) {
                    AccountsImportSelectionScreen(
                        importResult = result,
                        onImport = { tokens ->
                            onSetImportResult(null)
                            onImportAccounts(tokens)
                            onMessage(importSuccess)
                            onDismiss()
                        },
                        onDismiss = { onSetImportResult(null) }
                    )
                }
            }

            SettingsSubScreen.None -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SectionLabel(stringResource(R.string.settings_section_accounts))

                        if (onEditAccounts != null) {
                            SettingsRow(
                                title = stringResource(R.string.settings_edit_accounts),
                                enabled = accounts.isNotEmpty(),
                                onClick = onEditAccounts
                            )
                        }

                        SettingsRow(
                            title = stringResource(R.string.settings_export),
                            enabled = accounts.isNotEmpty(),
                            onClick = { onShowExport() }
                        )

                        SettingsRow(
                            title = stringResource(R.string.settings_import),
                            onClick = { showImport.value = true }
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        SectionLabel(stringResource(R.string.settings_section_data))

                        SettingsRow(
                            title = stringResource(R.string.settings_delete_all),
                            enabled = accounts.isNotEmpty(),
                            destructive = true,
                            onClick = { showDeleteAll.value = true }
                        )

                        SettingsRow(
                            title = stringResource(R.string.settings_erase_all),
                            destructive = true,
                            onClick = { showEraseAll.value = true }
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        SectionLabel(stringResource(R.string.settings_section_feedback))

                        SettingsRow(
                            title = stringResource(R.string.settings_rate_on_google_play),
                            isLink = true,
                            onClick = { openUrl(context, "https://play.google.com/store/apps/details?id=${context.packageName}") }
                        )

                        SettingsRow(
                            title = stringResource(R.string.settings_report_problem),
                            onClick = { showReportProblem.value = true }
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

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

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Bottom sheet и диалоги — за пределами AnimatedContent, рендерятся поверх
    if (showImport.value) {
        AppModalBottomSheet(
            appSheetState = importSheetState,
            onDismissRequest = { showImport.value = false }
        ) {
            AccountsImportPickerSheet(
                onFileParsed = { result ->
                    scope.launch {
                        importSheetState.hide()
                        showImport.value = false
                        onSetImportResult(result)
                    }
                },
                onError = { msg ->
                    scope.launch {
                        importSheetState.hide()
                        showImport.value = false
                    }
                    onMessage(msg)
                },
                onDismiss = {
                    scope.launch {
                        importSheetState.hide()
                        showImport.value = false
                    }
                }
            )
        }
    }

    if (showDeleteAll.value) {
        AlertDialog(
            onDismissRequest = { showDeleteAll.value = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteAll.value = false; onDeleteAll(); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll.value = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showEraseAll.value) {
        AlertDialog(
            onDismissRequest = { showEraseAll.value = false },
            title = { Text(stringResource(R.string.settings_erase_all_title)) },
            text = { Text(stringResource(R.string.settings_erase_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = { showEraseAll.value = false; onEraseAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_erase_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEraseAll.value = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReportProblem.value) {
        val supportEmail = "support@twinkey.app"
        AlertDialog(
            onDismissRequest = { showReportProblem.value = false },
            text = { Text(stringResource(R.string.settings_report_problem_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReportProblem.value = false
                    context.startActivity(Intent(Intent.ACTION_SENDTO).apply {
                        data = "mailto:$supportEmail".toUri()
                    })
                }) { Text(stringResource(R.string.settings_report_send_email)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportProblem.value = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("email", supportEmail))
                }) { Text(stringResource(R.string.settings_report_copy_address)) }
            }
        )
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
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isLink) {
            Icon(
                imageVector = Icons.Default.NorthEast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    HorizontalDivider()
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
