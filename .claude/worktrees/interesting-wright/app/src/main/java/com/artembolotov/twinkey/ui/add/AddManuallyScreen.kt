package com.artembolotov.twinkey.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.OtpAlgorithm
import com.artembolotov.twinkey.domain.OtpFactor
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.Token
import org.apache.commons.codec.binary.Base32
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Порт AddAccountManuallyView.swift.
 *
 * Форма: Issuer (сервис), Secret (Base32), Account (email),
 * Period (слайдер 5..180 шаг 5), Digits (6/7/8), Algorithm (SHA1/SHA256/SHA512).
 * Кнопка Done активна только при непустых Issuer + валидном Secret.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManuallyScreen(
    onDone: (Token) -> Unit,
    onCancel: () -> Unit
) {
    var issuer by remember { mutableStateOf("") }
    var secretRaw by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var periodFloat by remember { mutableFloatStateOf(30f) }
    var digits by remember { mutableStateOf(6) }
    var algorithm by remember { mutableStateOf(OtpAlgorithm.SHA1) }

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
        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(stringResource(R.string.add_manually_issuer)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        // Secret
        SectionHeader(stringResource(R.string.add_manually_section_secret))
        OutlinedTextField(
            value = secretRaw,
            onValueChange = { secretRaw = it.uppercase() },
            label = { Text(stringResource(R.string.add_manually_secret)) },
            singleLine = true,
            isError = secretRaw.isNotEmpty() && !secretValid,
            supportingText = if (secretRaw.isNotEmpty() && !secretValid) {
                { Text(stringResource(R.string.add_manually_secret_invalid)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Next
            )
        )

        // Account (optional)
        SectionHeader(stringResource(R.string.add_manually_section_account))
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text(stringResource(R.string.add_manually_account)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            )
        )

        // Time Interval — порт Stepper(value: $timeInterval, in: 5...180, step: 5)
        SectionHeader(stringResource(R.string.add_manually_section_period))
        val period = (periodFloat / 5).roundToInt() * 5
        Text(
            text = stringResource(R.string.add_manually_period_seconds, period),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = periodFloat,
            onValueChange = { periodFloat = it },
            valueRange = 5f..180f,
            steps = 34, // (180-5)/5 - 1 = 34
            modifier = Modifier.fillMaxWidth()
        )

        // Digits — порт Picker (сегментированный: 6 / 7 / 8)
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

        // Кнопки
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
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
