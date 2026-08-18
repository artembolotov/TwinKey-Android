package com.artembolotov.twinkey.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R

/**
 * Объяснение, почему аккаунты не приехали вместе с остальными данными приложения.
 *
 * Показывается вместо пустого экрана "аккаунтов пока нет", когда видно, что аккаунты
 * были: секреты лежат в Keystore и по устройствам не переносятся, единственный путь
 * назад — .twinkey-файл.
 */
@Composable
fun AccountsDataLostSheet(
    onRestoreFromBackup: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.accounts_lost_title),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.accounts_lost_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = onRestoreFromBackup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.accounts_restore_from_backup))
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.accounts_lost_dismiss))
        }

        Spacer(Modifier.height(8.dp))
    }
}
