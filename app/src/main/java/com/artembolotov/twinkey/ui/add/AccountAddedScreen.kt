package com.artembolotov.twinkey.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.OtpCodeView

/**
 * Порт AccountAddedScreen.swift + AccountAddedView.swift.
 *
 * Показывает: иконку щита, issuer, name, OtpCodeView (с tap-to-copy) и кнопку Done.
 */
@Composable
fun AccountAddedScreen(
    token: Token,
    code: String,
    secondsRemaining: Int,
    onDone: () -> Unit,
    onCopied: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Порт: Image(systemName: "checkmark.shield.fill")
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        // Issuer (сервис)
        Text(
            text = token.issuer.ifEmpty { token.name },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Account name
        if (token.name != token.issuer && token.name.isNotEmpty()) {
            Text(
                text = token.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // OTP-код: нажатие — копировать
        OtpCodeView(
            code = code,
            secondsRemaining = secondsRemaining,
            onTap = { copiedCode ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", copiedCode)))
                }
                onCopied()
            }
        )

        Text(
            text = stringResource(R.string.account_added_tap_to_copy_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // Порт: toolbar .Done button
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.account_added_done))
        }

        Spacer(Modifier.height(8.dp))
    }
}
