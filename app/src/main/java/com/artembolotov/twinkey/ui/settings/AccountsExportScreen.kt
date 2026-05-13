package com.artembolotov.twinkey.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.data.BackupManager
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.CheckableTokenRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsExportScreen(
    accounts: List<Token>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val allSelected = accounts.all { selected[it.id] == true }
    val exportState = remember { ExportState() }
    val exportErrorMsg = stringResource(R.string.backup_export_error)
    val selectedCount = accounts.count { selected[it.id] == true }

    val fileName = remember {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault())
        "${fmt.format(Date())}.twinkey"
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        exportState.exporting = false
        if (uri != null) {
            val result = writeBackupFile(context, uri, accounts.filter { selected[it.id] == true })
            if (result) onSuccess() else onError(exportErrorMsg)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.backup_export_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { exportState.exporting = true; createFileLauncher.launch(fileName) },
                        enabled = selectedCount > 0 && !exportState.exporting
                    ) {
                        Text(stringResource(R.string.backup_export_button, selectedCount))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(accounts, key = { it.id }) { token ->
                    CheckableTokenRow(
                        token = token,
                        checked = selected[token.id] ?: false,
                        onCheckedChange = { checked -> selected[token.id] = checked }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    if (allSelected) accounts.forEach { selected[it.id] = false }
                    else accounts.forEach { selected[it.id] = true }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (allSelected) stringResource(R.string.backup_select_none) else stringResource(R.string.backup_select_all))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private class ExportState {
    var exporting by mutableStateOf(false)
}

private fun writeBackupFile(context: Context, uri: Uri, tokens: List<Token>): Boolean {
    return try {
        val json = BackupManager.export(tokens)
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        true
    } catch (e: Exception) {
        Log.w("AccountsExport", "Failed to write backup file", e)
        false
    }
}
