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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import com.artembolotov.twinkey.data.ImportResult
import com.artembolotov.twinkey.data.SkipReason
import com.artembolotov.twinkey.domain.Token

/**
 * Порт AccountsImportScreen.swift.
 *
 * Шаг 1: Кнопка выбора файла → открывает системный file picker.
 * Шаг 2: После парсинга → список найденных аккаунтов с чекбоксами.
 * Опционально: предупреждение о пропущенных аккаунтах.
 */
@Composable
fun AccountsImportScreen(
    onImport: (List<Token>) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val importErrorMsg = stringResource(R.string.backup_import_error)

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = readBackupFile(context, uri)
            if (result != null) {
                importResult = result
                // По умолчанию выбрать все успешно распознанные
                result.successful.forEach { selected[it.id] = true }
            } else {
                onError(importErrorMsg)
            }
        }
    }

    val result = importResult

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (result == null) {
            // Шаг 1: предложение выбрать файл
            Text(
                text = stringResource(R.string.backup_import_title),
                style = MaterialTheme.typography.titleLarge
            )

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

        } else {
            // Шаг 2: предпросмотр найденных аккаунтов
            val allSelected = result.successful.all { selected[it.id] == true }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.backup_import_title),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = {
                    if (allSelected) result.successful.forEach { selected[it.id] = false }
                    else result.successful.forEach { selected[it.id] = true }
                }) {
                    Text(if (allSelected) stringResource(R.string.backup_select_none) else stringResource(R.string.backup_select_all))
                }
            }

            // Предупреждение о пропущенных
            if (result.skipped.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.backup_import_skipped, result.skipped.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Список успешно распознанных
            if (result.successful.isEmpty()) {
                Text(
                    text = stringResource(R.string.backup_import_no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    items(result.successful, key = { it.id }) { token ->
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
            }

            // Пропущенные аккаунты (read-only список с причинами)
            if (result.skipped.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.backup_import_skipped_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.skipped.forEach { skipped ->
                    val reason = when (val r = skipped.reason) {
                        is SkipReason.UnsupportedType -> stringResource(R.string.backup_skip_unsupported, r.typeName)
                        SkipReason.InvalidAccount -> stringResource(R.string.backup_skip_invalid)
                    }
                    Text(
                        text = "• ${skipped.name} — $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val selectedCount = result.successful.count { selected[it.id] == true }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val toImport = result.successful.filter { selected[it.id] == true }
                        onImport(toImport)
                    },
                    enabled = selectedCount > 0
                ) {
                    Text(stringResource(R.string.backup_import_button, selectedCount))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun readBackupFile(context: Context, uri: Uri): ImportResult? {
    return try {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        BackupManager.import(json)
    } catch (_: Exception) {
        null
    }
}
