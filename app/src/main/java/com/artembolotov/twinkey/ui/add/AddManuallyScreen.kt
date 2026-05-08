package com.artembolotov.twinkey.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.OtpAlgorithm
import com.artembolotov.twinkey.domain.OtpFactor
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.TextInputScreen
import org.apache.commons.codec.binary.Base32
import java.util.UUID

private enum class AddManuallyField { Issuer, Secret, Account }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManuallyScreen(
    onDone: (Token) -> Unit,
    onCancel: () -> Unit
) {
    var issuer by remember { mutableStateOf("") }
    var secretRaw by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var period by remember { mutableIntStateOf(30) }
    var digits by remember { mutableStateOf(6) }
    var algorithm by remember { mutableStateOf(OtpAlgorithm.SHA1) }
    var activeField: AddManuallyField? by remember { mutableStateOf(null) }

    val base32 = remember { Base32() }
    val secretValid by remember {
        derivedStateOf {
            val cleaned = secretRaw.trim().uppercase().replace(" ", "")
            cleaned.isNotEmpty() && runCatching { base32.decode(cleaned).isNotEmpty() }.getOrDefault(false)
        }
    }
    val canDone by remember { derivedStateOf { issuer.isNotBlank() && secretValid } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.add_manually_title),
            style = MaterialTheme.typography.titleLarge
        )

        // Service Provider
        SectionHeader(stringResource(R.string.add_manually_section_service))
        Box {
            TextField(
                value = issuer,
                onValueChange = {},
                label = { Text(stringResource(R.string.add_manually_issuer)) },
                singleLine = true,
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { activeField = AddManuallyField.Issuer }
            )
        }

        // Secret
        SectionHeader(stringResource(R.string.add_manually_section_secret))
        Box {
            TextField(
                value = secretRaw,
                onValueChange = {},
                label = { Text(stringResource(R.string.add_manually_secret)) },
                singleLine = true,
                readOnly = true,
                isError = secretRaw.isNotEmpty() && !secretValid,
                supportingText = if (secretRaw.isNotEmpty() && !secretValid) {
                    { Text(stringResource(R.string.add_manually_secret_invalid)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { activeField = AddManuallyField.Secret }
            )
        }

        // Account (optional)
        SectionHeader(stringResource(R.string.add_manually_section_account))
        Box {
            TextField(
                value = account,
                onValueChange = {},
                label = { Text(stringResource(R.string.add_manually_account)) },
                singleLine = true,
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { activeField = AddManuallyField.Account }
            )
        }

        // Time Interval
        SectionHeader(stringResource(R.string.add_manually_section_period))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.add_manually_period_seconds, period),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = { period -= 5 },
                enabled = period > 5,
                label = { Text("−") }
            )
            AssistChip(
                onClick = { period += 5 },
                enabled = period < 180,
                label = { Text("+") }
            )
        }

        // Digits
        SectionHeader(stringResource(R.string.add_manually_section_digits))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 7, 8).forEach { d ->
                FilterChip(
                    selected = digits == d,
                    onClick = { digits = d },
                    label = { Text("$d") }
                )
            }
        }

        // Algorithm
        SectionHeader(stringResource(R.string.add_manually_section_algorithm))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OtpAlgorithm.entries.forEach { alg ->
                FilterChip(
                    selected = algorithm == alg,
                    onClick = { algorithm = alg },
                    label = { Text(alg.name) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val secretBytes = base32.decode(
                        secretRaw.trim().uppercase().replace(" ", "")
                    )
                    val token = Token(
                        id = UUID.randomUUID().toString(),
                        name = account.trim().ifEmpty { issuer.trim() },
                        issuer = issuer.trim(),
                        generator = OtpGenerator(
                            secret = secretBytes,
                            factor = OtpFactor.Timer(period),
                            algorithm = algorithm,
                            digits = digits
                        )
                    )
                    onDone(token)
                },
                enabled = canDone
            ) {
                Text(stringResource(R.string.add_manually_done))
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // Full-screen dialog for single-field editing — mirrors iOS UIViewController push
    val currentField = activeField
    if (currentField != null) {
        Dialog(
            onDismissRequest = { activeField = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentField) {
                    AddManuallyField.Issuer -> TextInputScreen(
                        label = stringResource(R.string.add_manually_section_service),
                        initialValue = issuer,
                        placeholder = stringResource(R.string.add_manually_issuer),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        ),
                        doneLabel = stringResource(R.string.add_manually_done),
                        cancelLabel = stringResource(R.string.cancel),
                        onDone = { value ->
                            issuer = value
                            activeField = null
                        },
                        onCancel = { activeField = null }
                    )
                    AddManuallyField.Secret -> TextInputScreen(
                        label = stringResource(R.string.add_manually_section_secret),
                        initialValue = secretRaw,
                        placeholder = stringResource(R.string.add_manually_secret),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        doneLabel = stringResource(R.string.add_manually_done),
                        cancelLabel = stringResource(R.string.cancel),
                        onDone = { value ->
                            secretRaw = value.uppercase()
                            activeField = null
                        },
                        onCancel = { activeField = null }
                    )
                    AddManuallyField.Account -> TextInputScreen(
                        label = stringResource(R.string.add_manually_section_account),
                        initialValue = account,
                        placeholder = stringResource(R.string.add_manually_account),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        doneLabel = stringResource(R.string.add_manually_done),
                        cancelLabel = stringResource(R.string.cancel),
                        allowBlankDone = true,
                        onDone = { value ->
                            account = value
                            activeField = null
                        },
                        onCancel = { activeField = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
