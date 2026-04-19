package com.artembolotov.twinkey.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.data.BackupManager
import com.artembolotov.twinkey.domain.Token
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Порт AccountsExportScreen.swift.
 *
 * Список аккаунтов с чекбоксами. Кнопка "Выбрать все" / "Снять все".
 * Кнопка Export открывает системный диалог сохранения файла.
 */
@Composable
fun AccountsExportScreen(
    accounts: List<Token>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val allSelected = accounts.all { selected[it.id] == true }
    val exportState = remember { ExportState() }
    val exportErrorMsg = stringResource(R.string.backup_export_error)

    val fileName = remember {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault())
        "${fmt.format(Date())}.twinkey"
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        exportState.exporting = false
        if (uri != null) {
            val selectedTokens = accounts.filter { selected[it.id] == true }
            val result = writeBackupFile(context, uri, selectedTokens)
            if (result) onSuccess() else onError(exportErrorMsg)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок + кнопка выбора всех
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.backup_export_title),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = {
                if (allSelected) {
                    accounts.forEach { selected[it.id] = false }
                } else {
                    accounts.forEach { selected[it.id] = true }
                }
            }) {
                Text(
                    if (allSelected) stringResource(R.string.backup_select_none)
                    else stringResource(R.string.backup_select_all)
                )
            }
        }

        Text(
            text = stringResource(R.string.backup_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Список аккаунтов с чекбоксами
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(320.dp)
        ) {
            items(accounts, key = { it.id }) { token ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected[token.id] = !(selected[token.id] ?: false) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected[token.id] ?: false,
                        onCheckedChange = { checked -> selected[token.id] = checked }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = token.issuer.ifEmpty { token.name },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (token.name.isNotEmpty() && token.name != token.issuer) {
                            Text(
                                text = token.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        val selectedCount = accounts.count { selected[it.id] == true }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    exportState.exporting = true
                    createFileLauncher.launch(fileName)
                },
                enabled = selectedCount > 0 && !exportState.exporting
            ) {
                Text(stringResource(R.string.backup_export_button, selectedCount))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private class ExportState {
    var exporting by mutableStateOf(false)
}

private fun writeBackupFile(context: Context, uri: Uri, tokens: List<Token>): Boolean {
    return try {
        val json = BackupManager.export(tokens)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (_: Exception) {
        false
    }
}
