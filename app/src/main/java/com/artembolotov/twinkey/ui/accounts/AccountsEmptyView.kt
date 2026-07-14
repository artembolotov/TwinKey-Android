package com.artembolotov.twinkey.ui.accounts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.artembolotov.twinkey.R

// Порт twinkey://add-test из iOS: ссылка в подсказке добавляет тестовый аккаунт
const val ADD_TEST_ACCOUNT_URL = "twinkey://add-test"

@Composable
fun AccountsEmptyView(
    onRestoreFromBackup: () -> Unit,
    onAddTestAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIcon = remember {
        BitmapPainter(
            context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Image(
            painter = appIcon,
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.accounts_no_codes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onRestoreFromBackup) {
            Text(stringResource(R.string.accounts_restore_from_backup))
        }

        val uriHandler = LocalUriHandler.current
        val hintHtml = stringResource(R.string.accounts_google_auth_import_hint)
        val linkColor = MaterialTheme.colorScheme.primary
        val hint = remember(hintHtml, linkColor) {
            AnnotatedString.fromHtml(
                htmlString = hintHtml,
                linkStyles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                ),
                linkInteractionListener = { link ->
                    val url = (link as? LinkAnnotation.Url)?.url
                    when {
                        url == ADD_TEST_ACCOUNT_URL -> onAddTestAccount()
                        url != null -> runCatching { uriHandler.openUri(url) }
                    }
                }
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(32.dp))
    }
}
