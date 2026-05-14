package com.artembolotov.twinkey.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.graphics.Color
import com.artembolotov.twinkey.ui.components.GlassScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.data.BackupManager
import com.artembolotov.twinkey.data.ImportResult
import com.artembolotov.twinkey.data.SkipReason
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.CheckableTokenRow

/** Шаг 1: только выбор файла. Используется внутри bottom sheet. */
@Composable
fun AccountsImportPickerSheet(
    onFileParsed: (ImportResult) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val importErrorMsg = stringResource(R.string.backup_import_error)

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = readBackupFile(context, uri)
            if (result != null) onFileParsed(result) else onError(importErrorMsg)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.backup_import_title), style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.backup_import_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { openFileLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.backup_import_choose_file))
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Шаг 2: выбор аккаунтов с чекбоксами. Полноэкранный. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsImportSelectionScreen(
    importResult: ImportResult,
    onImport: (List<Token>) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val selected = remember { mutableStateMapOf<String, Boolean>().also { map ->
        importResult.successful.forEach { map[it.id] = true }
    }}
    val allSelected = importResult.successful.all { selected[it.id] == true }
    val selectedCount = importResult.successful.count { selected[it.id] == true }

    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }

    GlassScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.backup_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (importResult.successful.isNotEmpty()) {
                        TextButton(onClick = {
                            if (allSelected) importResult.successful.forEach { selected[it.id] = false }
                            else importResult.successful.forEach { selected[it.id] = true }
                        }) {
                            Text(if (allSelected) stringResource(R.string.backup_select_none) else stringResource(R.string.backup_select_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomBarHeightDp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                if (importResult.skipped.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.backup_import_skipped, importResult.skipped.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (importResult.successful.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.backup_import_no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(importResult.successful, key = { it.id }) { token ->
                        CheckableTokenRow(
                            token = token,
                            checked = selected[token.id] ?: false,
                            onCheckedChange = { checked -> selected[token.id] = checked }
                        )
                    }
                }

                if (importResult.skipped.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.backup_import_skipped_section), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        importResult.skipped.forEach { skipped ->
                            val reason = when (val r = skipped.reason) {
                                is SkipReason.UnsupportedType -> stringResource(R.string.backup_skip_unsupported, r.typeName)
                                SkipReason.InvalidAccount -> stringResource(R.string.backup_skip_invalid)
                            }
                            Text("• ${skipped.name} — $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp)
            ) {
                Button(
                    onClick = { onImport(importResult.successful.filter { selected[it.id] == true }) },
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.backup_import_button, selectedCount))
                }
            }
        }
    }
}

private fun readBackupFile(context: Context, uri: Uri): ImportResult? {
    return try {
        val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        BackupManager.import(json)
    } catch (e: Exception) {
        Log.w("AccountsImport", "Failed to read backup file", e)
        null
    }
}
