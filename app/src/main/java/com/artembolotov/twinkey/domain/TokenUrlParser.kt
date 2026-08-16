package com.artembolotov.twinkey.domain

import org.apache.commons.codec.binary.Base32
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Порт Token+URL.swift.
 *
 * Парсит и сериализует otpauth:// URL по единой спецификации бэкапа —
 * см. docs/BACKUP_FORMAT.md. Каноническая форма:
 *
 *   otpauth://totp/<issuer>:<name>?algorithm=SHA1&digits=6&issuer=<issuer>&period=30
 *
 * issuer и name percent-кодируются по RFC 3986 по отдельности (не URLEncoder:
 * тот пишет пробел как '+'), двоеточие между ними остаётся незакодированным.
 * Секрет в URL не пишется — он лежит отдельно в CodableToken.secret.
 *
 * Чтение намеренно более снисходительное, чем запись: старые бэкапы
 * (iOS — только name в label, Android — form-encoded "issuer%3Aname")
 * обязаны продолжать открываться, см. §4 спецификации.
 *
 * Namеренно не использует android.net.Uri / java.net.URI,
 * чтобы TokenUrlParser был тестируем в JVM unit-тестах без Android-зависимостей.
 */
object TokenUrlParser {

    private const val OTP_SCHEME = "otpauth"
    private const val FACTOR_TOTP = "totp"
    private const val FACTOR_HOTP = "hotp"

    /** RFC 3986 unreserved: всё остальное кодируется как %XX. */
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** Instagram пишет label как "username:artem" — префикс не является issuer. */
    private val USERNAME_PREFIX = Regex("^username(:|%3A)", RegexOption.IGNORE_CASE)

    sealed class ParseError(message: String) : Exception(message) {
        class BadUrl : ParseError("Bad URL")
        class InvalidScheme : ParseError("Invalid URL scheme, expected otpauth://")
        class MissingFactor : ParseError("Missing factor (host must be 'totp' or 'hotp')")
        class InvalidFactor(val value: String) : ParseError("Invalid factor: $value")
        class MissingSecret : ParseError("Missing secret")
        class InvalidSecret(val value: String) : ParseError("Invalid Base32 secret: $value")
        class InvalidAlgorithm(val value: String) : ParseError("Unknown algorithm: $value")
    }

    // --- Parse ---

    /** Парсит otpauth:// URL с секретом в query (QR-код, ручной ввод). */
    fun parse(urlString: String): Token = parse(urlString, secretOverride = null)

    /**
     * @param secretOverride секрет из отдельного поля (бэкап/keychain).
     *   Если задан, query-параметр `secret` не требуется и игнорируется.
     */
    private fun parse(urlString: String, secretOverride: ByteArray?): Token {
        val url = ParsedUrl.from(urlString) ?: throw ParseError.BadUrl()
        if (!url.scheme.equals(OTP_SCHEME, ignoreCase = true)) throw ParseError.InvalidScheme()

        val params = url.queryParams()

        val factor: OtpFactor = when (url.host.lowercase()) {
            FACTOR_TOTP -> OtpFactor.Timer(params["period"]?.toIntOrNull() ?: 30)
            FACTOR_HOTP -> OtpFactor.Counter(params["counter"]?.toLongOrNull() ?: 0L)
            "" -> throw ParseError.MissingFactor()
            else -> throw ParseError.InvalidFactor(url.host)
        }

        val secret = secretOverride ?: decodeBase32(params["secret"] ?: throw ParseError.MissingSecret())
        if (secret.isEmpty()) throw ParseError.MissingSecret()

        val algorithm = when (params["algorithm"]?.uppercase()) {
            null, "SHA1" -> OtpAlgorithm.SHA1
            "SHA256" -> OtpAlgorithm.SHA256
            "SHA512" -> OtpAlgorithm.SHA512
            else -> throw ParseError.InvalidAlgorithm(params["algorithm"]!!)
        }

        val digits = params["digits"]?.toIntOrNull() ?: 6

        val generator = OtpGenerator(
            secret = secret,
            factor = factor,
            algorithm = algorithm,
            digits = digits
        )

        val queryIssuer = params["issuer"]
        val (labelIssuer, name) = splitLabel(url.rawLabel, queryIssuer)

        return Token(name = name, issuer = queryIssuer ?: labelIssuer, generator = generator)
    }

    // --- Serialize ---

    /**
     * Сериализует Token в канонический otpauth:// URL без секрета (как iOS urlForToken).
     * Секрет хранится отдельно в CodableToken.secret.
     */
    fun toUrl(token: Token): String {
        val g = token.generator
        val factorType = when (g.factor) {
            is OtpFactor.Timer -> FACTOR_TOTP
            is OtpFactor.Counter -> FACTOR_HOTP
        }
        val algorithmStr = when (g.algorithm) {
            OtpAlgorithm.SHA1 -> "SHA1"
            OtpAlgorithm.SHA256 -> "SHA256"
            OtpAlgorithm.SHA512 -> "SHA512"
        }
        // issuer и name кодируются по отдельности, ':' между ними — незакодированный
        val label = if (token.issuer.isEmpty()) {
            percentEncode(token.name)
        } else {
            percentEncode(token.issuer) + ":" + percentEncode(token.name)
        }

        val factorParam = when (val f = g.factor) {
            is OtpFactor.Timer -> "period=${f.period}"
            is OtpFactor.Counter -> "counter=${f.value}"
        }

        return "$OTP_SCHEME://$factorType/$label" +
                "?algorithm=$algorithmStr" +
                "&digits=${g.digits}" +
                "&issuer=${percentEncode(token.issuer)}" +
                "&$factorParam"
    }

    // --- CodableToken ↔ Token (совместимость с iOS backup) ---

    fun toCodableToken(token: Token): CodableToken = CodableToken(
        url = toUrl(token),
        secret = Base64.getEncoder().encodeToString(token.generator.secret)
    )

    fun fromCodableToken(codable: CodableToken, id: String): Token {
        val secret = decodeBase64(codable.secret)
        if (secret.isEmpty()) throw ParseError.MissingSecret()
        return parse(codable.url, secretOverride = secret).copy(id = id)
    }

    // --- Percent-encoding (RFC 3986) ---

    /**
     * Percent-кодирование по RFC 3986: незакодированными остаются только unreserved,
     * остальные байты UTF-8 → %XX в верхнем регистре.
     *
     * Это не URLEncoder: тот использует form-encoding и пишет пробел как '+',
     * из-за чего пробел в issuer становится неотличим от плюса в имени (§5 спеки).
     */
    fun percentEncode(value: String): String = buildString(value.length) {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (char in UNRESERVED) append(char)
            else append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
    }

    /**
     * Декодирует %XX. '+' остаётся плюсом: в старых Android-бэкапах пробел писался
     * как '+' и неотличим от настоящего плюса (например, в номере телефона), поэтому
     * читатель не угадывает — плюс сохраняется как есть (§5 спеки).
     */
    fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ByteArrayOutputStream(value.length)
        val chunk = StringBuilder()
        fun flushChunk() {
            if (chunk.isNotEmpty()) {
                bytes.write(chunk.toString().toByteArray(Charsets.UTF_8))
                chunk.setLength(0)
            }
        }
        var i = 0
        while (i < value.length) {
            val byte = if (value[i] == '%' && i + 2 < value.length) {
                hexPair(value[i + 1], value[i + 2])
            } else null

            if (byte != null) {
                flushChunk()
                bytes.write(byte)
                i += 3
            } else {
                chunk.append(value[i])
                i++
            }
        }
        flushChunk()
        return bytes.toString(Charsets.UTF_8.name())
    }

    // --- Private helpers ---

    private const val HEX = "0123456789ABCDEF"

    private fun hexPair(high: Char, low: Char): Int? {
        val h = hexDigit(high) ?: return null
        val l = hexDigit(low) ?: return null
        return (h shl 4) or l
    }

    private fun hexDigit(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }

    /**
     * Разбирает label по §4 спеки: сначала — как каноническую форму
     * "<issuer>:<name>" с незакодированным двоеточием, иначе — как legacy-форму
     * (голое имя из старого iOS либо "issuer%3Aname" из старого Android).
     *
     * @return issuer из label (пустой, если его там нет) и name.
     */
    private fun splitLabel(rawLabel: String, queryIssuer: String?): Pair<String, String> {
        val label = rawLabel.replaceFirst(USERNAME_PREFIX, "")

        // 1. Незакодированное двоеточие → каноническая форма, режем по первому
        val colon = label.indexOf(':')
        if (colon >= 0) {
            return percentDecode(label.substring(0, colon)) to
                    percentDecode(label.substring(colon + 1)).trim()
        }

        // 2. Иначе декодируем целиком и снимаем префикс "<issuer>:", если он есть
        val decoded = percentDecode(label)
        if (!queryIssuer.isNullOrEmpty()) {
            val prefix = "$queryIssuer:"
            if (decoded.startsWith(prefix)) {
                return queryIssuer to decoded.removePrefix(prefix).trim()
            }
        }
        return "" to decoded
    }

    private fun decodeBase32(value: String): ByteArray = try {
        Base32().decode(value.uppercase().replace(" ", ""))
    } catch (_: Exception) {
        throw ParseError.InvalidSecret(value)
    }

    /** Стандартный Base64 по спеке; base64url принимается как поблажка старым файлам. */
    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        Base64.getUrlDecoder().decode(value)
    }

    /**
     * Минимальный разбор "scheme://host/label?query" по сырой строке.
     * Сырой label нужен, чтобы отличить незакодированное ':' от '%3A' (§4 спеки),
     * а ручной разбор — чтобы не спотыкаться о URL из чужих QR-кодов,
     * которые java.net.URI считает синтаксически невалидными.
     */
    private class ParsedUrl(
        val scheme: String,
        val host: String,
        val rawLabel: String,
        val rawQuery: String
    ) {
        fun queryParams(): Map<String, String> =
            rawQuery.split("&").mapNotNull { param ->
                val eq = param.indexOf('=')
                if (eq < 0) return@mapNotNull null
                TokenUrlParser.percentDecode(param.substring(0, eq)) to
                        TokenUrlParser.percentDecode(param.substring(eq + 1))
            }.toMap()

        companion object {
            fun from(urlString: String): ParsedUrl? {
                val input = urlString.trim()
                val schemeEnd = input.indexOf("://")
                if (schemeEnd <= 0) return null
                val scheme = input.substring(0, schemeEnd)

                var rest = input.substring(schemeEnd + 3)
                rest = rest.substringBefore('#')

                val query = rest.substringAfter('?', "")
                val hostAndLabel = rest.substringBefore('?')

                val slash = hostAndLabel.indexOf('/')
                val host = if (slash < 0) hostAndLabel else hostAndLabel.substring(0, slash)
                val label = if (slash < 0) "" else hostAndLabel.substring(slash + 1)

                return ParsedUrl(scheme = scheme, host = host, rawLabel = label, rawQuery = query)
            }
        }
    }
}
