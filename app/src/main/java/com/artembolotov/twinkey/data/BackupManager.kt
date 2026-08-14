package com.artembolotov.twinkey.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.artembolotov.twinkey.domain.CodableToken
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TokenUrlParser
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Порт BackupDocument.swift + BackupImporter.swift.
 *
 * Формат .twinkey: JSON-массив CodableToken, совместимый с iOS.
 * Каждый элемент: { "url": "otpauth://...", "secret": "<base64>" }
 */
object BackupManager {

    private val json = Json { ignoreUnknownKeys = true }

    /** Сериализует выбранные токены в JSON-строку для записи в .twinkey файл */
    fun export(tokens: List<Token>): String {
        val codable = tokens.map { TokenUrlParser.toCodableToken(it) }
        return json.encodeToString(codable)
    }

    /**
     * Десериализует JSON из .twinkey файла.
     *
     * Контракт ошибок:
     *  - Невалидный JSON или пустой массив → бросает [IllegalArgumentException].
     *    Эти случаи означают, что файл целиком нечитаем как backup, и caller
     *    показывает пользователю общую ошибку чтения.
     *  - Отдельный элемент с невалидным URL или неподдерживаемого типа (HOTP) →
     *    попадает в [ImportResult.skipped], остальные элементы возвращаются
     *    в [ImportResult.successful].
     */
    fun import(jsonString: String): ImportResult {
        val codableList = try {
            json.decodeFromString<List<CodableToken>>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup file: ${e.message}")
        }

        if (codableList.isEmpty()) throw IllegalArgumentException("Backup file contains no accounts")

        val successful = mutableListOf<Token>()
        val skipped = mutableListOf<SkippedAccount>()

        for (codable in codableList) {
            try {
                val token = TokenUrlParser.fromCodableToken(codable, UUID.randomUUID().toString())
                // Проверяем тип: HOTP не поддерживается (как в iOS)
                if (codable.url.startsWith("otpauth://hotp/")) {
                    skipped += SkippedAccount(
                        name = token.issuer.ifEmpty { token.name },
                        reason = SkipReason.UnsupportedType("HOTP")
                    )
                } else {
                    successful += token
                }
            } catch (_: Exception) {
                // Попытаться извлечь имя из URL для отображения в skipped
                val displayName = extractDisplayName(codable.url)
                skipped += SkippedAccount(
                    name = displayName,
                    reason = SkipReason.InvalidAccount
                )
            }
        }

        return ImportResult(successful = successful, skipped = skipped)
    }

    /**
     * Читает .twinkey файл по SAF/content Uri и парсит его через [import].
     * Возвращает null при ошибке чтения или нечитаемом формате — вызывающая сторона
     * показывает общее сообщение об ошибке.
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult? {
        return try {
            val json = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return null
            import(json)
        } catch (e: Exception) {
            Log.w("BackupManager", "Failed to read backup file", e)
            null
        }
    }

    private fun extractDisplayName(url: String): String {
        return try {
            val path = url.substringAfter("://").substringAfter("/").substringBefore("?")
            java.net.URLDecoder.decode(path, "UTF-8")
                .substringAfter(":").ifEmpty { path }
        } catch (_: Exception) { url }
    }
}

data class ImportResult(
    val successful: List<Token>,
    val skipped: List<SkippedAccount>
)

data class SkippedAccount(
    val name: String,
    val reason: SkipReason
)

sealed class SkipReason {
    data class UnsupportedType(val typeName: String) : SkipReason()
    object InvalidAccount : SkipReason()
}
