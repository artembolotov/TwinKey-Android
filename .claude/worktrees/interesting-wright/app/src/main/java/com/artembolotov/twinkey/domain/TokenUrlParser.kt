package com.artembolotov.twinkey.domain

import org.apache.commons.codec.binary.Base32
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * Порт Token+URL.swift.
 *
 * Парсит и сериализует otpauth:// URL.
 * Формат: otpauth://totp/Issuer:name?secret=BASE32&issuer=Issuer&algorithm=SHA1&digits=6&period=30
 *
 * Намеренно использует java.net.URI (не android.net.Uri),
 * чтобы TokenUrlParser был тестируем в JVM unit-тестах без Android-зависимостей.
 */
object TokenUrlParser {

    private const val OTP_SCHEME = "otpauth"
    private const val FACTOR_TOTP = "totp"
    private const val FACTOR_HOTP = "hotp"

    sealed class ParseError(message: String) : Exception(message) {
        object BadUrl : ParseError("Bad URL")
        object InvalidScheme : ParseError("Invalid URL scheme, expected otpauth://")
        object MissingFactor : ParseError("Missing factor (host must be 'totp' or 'hotp')")
        data class InvalidFactor(val value: String) : ParseError("Invalid factor: $value")
        object MissingSecret : ParseError("Missing secret")
        data class InvalidSecret(val value: String) : ParseError("Invalid Base32 secret: $value")
        data class InvalidAlgorithm(val value: String) : ParseError("Unknown algorithm: $value")
    }

    // MARK: - Parse

    fun parse(urlString: String): Token {
        val uri = try { URI(urlString) } catch (e: Exception) { throw ParseError.BadUrl }
        if (uri.scheme != OTP_SCHEME) throw ParseError.InvalidScheme

        val params = uri.queryParams()

        val factor: OtpFactor = when (uri.host?.lowercase()) {
            FACTOR_TOTP -> OtpFactor.Timer(params["period"]?.toIntOrNull() ?: 30)
            FACTOR_HOTP -> OtpFactor.Counter(params["counter"]?.toLongOrNull() ?: 0L)
            null -> throw ParseError.MissingFactor
            else -> throw ParseError.InvalidFactor(uri.host!!)
        }

        val secretParam = params["secret"] ?: throw ParseError.MissingSecret
        val secret = try {
            Base32().decode(secretParam.uppercase())
        } catch (e: Exception) {
            throw ParseError.InvalidSecret(secretParam)
        }
        if (secret.isEmpty()) throw ParseError.MissingSecret

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

        // Путь: пропустить ведущий "/" → "Issuer:username" или просто "username"
        // Дополнительно: убрать Instagram-префикс "username:" (как в iOS Token+URL.swift)
        val rawPath = uri.path?.removePrefix("/") ?: ""
        val fullName = URLDecoder.decode(rawPath, "UTF-8")
            .replace(Regex("^username:", RegexOption.IGNORE_CASE), "")

        val issuer = params["issuer"]
            ?: run {
                val colonIndex = fullName.indexOf(':')
                if (colonIndex >= 0) fullName.substring(0, colonIndex) else ""
            }

        val name = trimIssuerPrefix(issuer, fullName)

        return Token(name = name, issuer = issuer, generator = generator)
    }

    // MARK: - Serialize

    /**
     * Сериализует Token в otpauth:// URL без секрета (как iOS urlForToken).
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
        val label = if (token.issuer.isNotEmpty()) "${token.issuer}:${token.name}" else token.name
        val encodedLabel = URLEncoder.encode(label, "UTF-8")

        val factorParam = when (val f = g.factor) {
            is OtpFactor.Timer -> "period=${f.period}"
            is OtpFactor.Counter -> "counter=${f.value}"
        }

        return "$OTP_SCHEME://$factorType/$encodedLabel" +
                "?algorithm=$algorithmStr" +
                "&digits=${g.digits}" +
                "&issuer=${URLEncoder.encode(token.issuer, "UTF-8")}" +
                "&$factorParam"
    }

    // MARK: - CodableToken ↔ Token (совместимость с iOS backup)

    fun toCodableToken(token: Token): CodableToken = CodableToken(
        url = toUrl(token),
        secret = Base64.getEncoder().encodeToString(token.generator.secret)
    )

    fun fromCodableToken(codable: CodableToken, id: String): Token {
        val secret = Base64.getDecoder().decode(codable.secret)
        // Парсим URL чтобы получить name/issuer/algorithm/digits/period,
        // затем подставляем секрет из отдельного поля
        val parsed = parse(codable.url + "&secret=" + Base32().encodeAsString(secret).trimEnd('='))
        return parsed.copy(id = id)
    }

    // MARK: - Private helpers

    private fun URI.queryParams(): Map<String, String> =
        (rawQuery ?: "").split("&").mapNotNull { param ->
            val eq = param.indexOf('=')
            if (eq < 0) return@mapNotNull null
            val key = URLDecoder.decode(param.substring(0, eq), "UTF-8")
            val value = URLDecoder.decode(param.substring(eq + 1), "UTF-8")
            key to value
        }.toMap()

    private fun trimIssuerPrefix(issuer: String, fullName: String): String {
        if (issuer.isNotEmpty()) {
            val prefix = "$issuer:"
            if (fullName.startsWith(prefix)) {
                return fullName.removePrefix(prefix).trim()
            }
        }
        return fullName
    }
}
