package com.artembolotov.twinkey.domain

import android.net.Uri
import java.util.UUID

/**
 * Парсер Google Authenticator Migration QR-кодов.
 *
 * URL формат: otpauth-migration://offline?data=<BASE64URL_ENCODED_PROTOBUF>
 *
 * Protobuf структура MigrationPayload:
 *   repeated OtpParameters otp_parameters = 1;
 *
 * OtpParameters:
 *   bytes  secret      = 1
 *   string name        = 2
 *   string issuer      = 3
 *   enum   algorithm   = 4  (1=SHA1, 2=SHA256, 3=SHA512)
 *   enum   digit_count = 5  (1=SIX, 2=EIGHT)
 *   enum   type        = 6  (1=HOTP, 2=TOTP)
 *   int64  counter     = 7
 *
 * Минимальный protobuf-декодер без зависимостей.
 */
object GoogleAuthMigrationParser {

    fun isMigrationUrl(url: String) = url.startsWith("otpauth-migration://")

    /**
     * Парсит URL и возвращает Pair(успешные токены, пропущенные имена).
     * Пропущенные — HOTP аккаунты (не поддерживаются).
     */
    fun parse(url: String): Pair<List<Token>, List<String>> {
        val data = Uri.parse(url).getQueryParameter("data")
            ?: throw IllegalArgumentException("Missing 'data' parameter in migration URL")

        // Base64URL → bytes
        val bytes = android.util.Base64.decode(
            data.replace('-', '+').replace('_', '/'),
            android.util.Base64.DEFAULT
        )

        val params = decodeMigrationPayload(bytes)

        val tokens = mutableListOf<Token>()
        val skipped = mutableListOf<String>()

        for (p in params) {
            val displayName = p.issuer.ifEmpty { p.name }
            when {
                p.type == 1 -> skipped += displayName  // HOTP не поддерживается
                p.secret.isEmpty() -> skipped += displayName
                else -> {
                    val algorithm = when (p.algorithm) {
                        2 -> OtpAlgorithm.SHA256
                        3 -> OtpAlgorithm.SHA512
                        else -> OtpAlgorithm.SHA1
                    }
                    val digits = if (p.digitCount == 2) 8 else 6
                    tokens += Token(
                        id = UUID.randomUUID().toString(),
                        name = p.name,
                        issuer = p.issuer,
                        generator = OtpGenerator(
                            secret = p.secret,
                            factor = OtpFactor.Timer(30),
                            algorithm = algorithm,
                            digits = digits
                        )
                    )
                }
            }
        }

        return Pair(tokens, skipped)
    }

    // MARK: - Protobuf decoder

    private data class OtpParams(
        var secret: ByteArray = ByteArray(0),
        var name: String = "",
        var issuer: String = "",
        var algorithm: Int = 1,
        var digitCount: Int = 1,
        var type: Int = 2,
        var counter: Long = 0L
    )

    private fun decodeMigrationPayload(bytes: ByteArray): List<OtpParams> {
        val result = mutableListOf<OtpParams>()
        var pos = 0

        while (pos < bytes.size) {
            val tag = readVarint(bytes, pos)
            pos += tag.second
            val fieldNumber = (tag.first shr 3).toInt()
            val wireType = (tag.first and 0x7L).toInt()

            when {
                // Field 1 = repeated OtpParameters (wire type 2 = length-delimited)
                fieldNumber == 1 && wireType == 2 -> {
                    val len = readVarint(bytes, pos)
                    pos += len.second
                    val subBytes = bytes.copyOfRange(pos, pos + len.first.toInt())
                    pos += len.first.toInt()
                    result += decodeOtpParameters(subBytes)
                }
                wireType == 0 -> { val v = readVarint(bytes, pos); pos += v.second }
                wireType == 2 -> { val l = readVarint(bytes, pos); pos += l.second + l.first.toInt() }
                wireType == 5 -> { pos += 4 }
                wireType == 1 -> { pos += 8 }
                else -> break
            }
        }
        return result
    }

    private fun decodeOtpParameters(bytes: ByteArray): OtpParams {
        val p = OtpParams()
        var pos = 0

        while (pos < bytes.size) {
            val tag = readVarint(bytes, pos)
            pos += tag.second
            val fieldNumber = (tag.first shr 3).toInt()
            val wireType = (tag.first and 0x7L).toInt()

            when {
                fieldNumber == 1 && wireType == 2 -> {  // secret (bytes)
                    val len = readVarint(bytes, pos); pos += len.second
                    p.secret = bytes.copyOfRange(pos, pos + len.first.toInt())
                    pos += len.first.toInt()
                }
                fieldNumber == 2 && wireType == 2 -> {  // name (string)
                    val len = readVarint(bytes, pos); pos += len.second
                    p.name = String(bytes, pos, len.first.toInt(), Charsets.UTF_8)
                    pos += len.first.toInt()
                }
                fieldNumber == 3 && wireType == 2 -> {  // issuer (string)
                    val len = readVarint(bytes, pos); pos += len.second
                    p.issuer = String(bytes, pos, len.first.toInt(), Charsets.UTF_8)
                    pos += len.first.toInt()
                }
                fieldNumber == 4 && wireType == 0 -> {  // algorithm (enum)
                    val v = readVarint(bytes, pos); pos += v.second
                    p.algorithm = v.first.toInt()
                }
                fieldNumber == 5 && wireType == 0 -> {  // digit_count (enum)
                    val v = readVarint(bytes, pos); pos += v.second
                    p.digitCount = v.first.toInt()
                }
                fieldNumber == 6 && wireType == 0 -> {  // type (enum)
                    val v = readVarint(bytes, pos); pos += v.second
                    p.type = v.first.toInt()
                }
                fieldNumber == 7 && wireType == 0 -> {  // counter (int64)
                    val v = readVarint(bytes, pos); pos += v.second
                    p.counter = v.first
                }
                wireType == 0 -> { val v = readVarint(bytes, pos); pos += v.second }
                wireType == 2 -> { val l = readVarint(bytes, pos); pos += l.second + l.first.toInt() }
                wireType == 5 -> { pos += 4 }
                wireType == 1 -> { pos += 8 }
                else -> break
            }
        }
        return p
    }

    /** Читает varint из bytes начиная с offset. Возвращает (value, bytesRead). */
    private fun readVarint(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (pos < bytes.size) {
            val b = bytes[pos++].toLong() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            shift += 7
            if (b and 0x80 == 0L) break
        }
        return Pair(result, pos - offset)
    }
}
