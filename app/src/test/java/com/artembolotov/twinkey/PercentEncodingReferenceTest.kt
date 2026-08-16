package com.artembolotov.twinkey

import com.artembolotov.twinkey.domain.OtpAlgorithm
import com.artembolotov.twinkey.domain.OtpFactor
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TokenUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * Сверка кодировщика с эталоном из docs/BACKUP_FORMAT.md §6.
 *
 * [SpecReference] — дословная копия кода из спецификации. Тесты доказывают, что
 * TokenUrlParser даёт ровно тот же результат: на каждой кодовой точке Unicode,
 * на случайных строках и на целых URL. Если спецификацию поправят, здесь
 * обновляется эталон, и расхождение всплывёт сразу.
 */
class PercentEncodingReferenceTest {

    /** Дословно из docs/BACKUP_FORMAT.md §6 — менять только вслед за спецификацией. */
    private object SpecReference {

        private const val UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /** RFC 3986 percent-encoding. Not URLEncoder: that one writes a space as '+'. */
        fun percentEncode(value: String): String = buildString {
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val char = byte.toInt().toChar()
                if (char in UNRESERVED) append(char)
                else append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }

        fun toUri(name: String, issuer: String, algorithm: String, digits: Int, period: Int): String {
            val label =
                if (issuer.isEmpty()) percentEncode(name)
                else percentEncode(issuer) + ":" + percentEncode(name)
            return "otpauth://totp/$label" +
                    "?algorithm=$algorithm&digits=$digits&issuer=${percentEncode(issuer)}&period=$period"
        }
    }

    // ---- Кодировщик ----

    @Test
    fun `percentEncode matches the reference for every Unicode code point`() {
        var checked = 0
        for (codePoint in 0..0x10FFFF) {
            // Непарные суррогаты — не текст, UTF-8 их не представляет
            if (codePoint in 0xD800..0xDFFF) continue
            val value = String(Character.toChars(codePoint))
            assertEquals(
                "code point U+%04X".format(Locale.ROOT, codePoint),
                SpecReference.percentEncode(value),
                TokenUrlParser.percentEncode(value)
            )
            checked++
        }
        assertEquals(0x10FFFF + 1 - 2048, checked)
    }

    @Test
    fun `percentEncode matches the reference on random strings`() {
        val random = Random(20260816)
        repeat(20_000) {
            val value = randomString(random)
            assertEquals(SpecReference.percentEncode(value), TokenUrlParser.percentEncode(value))
        }
    }

    @Test
    fun `percentEncode output is unreserved plus uppercase escapes only`() {
        val random = Random(1)
        val allowed = Regex("[A-Za-z0-9\\-._~%]*")
        val escape = Regex("%[0-9A-F]{2}")
        repeat(5_000) {
            val encoded = TokenUrlParser.percentEncode(randomString(random))
            assertTrue(encoded, allowed.matches(encoded))
            // каждый '%' — начало корректного экранирования из двух ЗАГЛАВНЫХ hex-цифр
            var i = 0
            while (i < encoded.length) {
                if (encoded[i] == '%') {
                    assertTrue(encoded, escape.matches(encoded.substring(i, minOf(i + 3, encoded.length))))
                    i += 3
                } else i++
            }
        }
    }

    @Test
    fun `percentEncode does not depend on the default locale`() {
        // Локали с собственными цифрами: форматирование hex не должно за ними следовать
        val locales = listOf(
            Locale.forLanguageTag("ar-EG-u-nu-arab"),
            Locale.forLanguageTag("hi-IN-u-nu-deva"),
            Locale.forLanguageTag("bn-BD-u-nu-beng"),
            Locale.forLanguageTag("tr-TR")
        )
        val original = Locale.getDefault()
        try {
            for (locale in locales) {
                Locale.setDefault(locale)
                assertEquals(locale.toString(), "%20%2B%40", TokenUrlParser.percentEncode(" +@"))
                assertEquals(
                    locale.toString(),
                    "%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8",
                    TokenUrlParser.percentEncode("Госуслуги")
                )
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `percentDecode undoes percentEncode`() {
        val random = Random(7)
        repeat(20_000) {
            val value = randomString(random)
            assertEquals(value, TokenUrlParser.percentDecode(TokenUrlParser.percentEncode(value)))
        }
    }

    // ---- Целый URL ----

    @Test
    fun `toUrl matches the reference toUri`() {
        val random = Random(4242)
        val algorithms = listOf(
            OtpAlgorithm.SHA1 to "SHA1",
            OtpAlgorithm.SHA256 to "SHA256",
            OtpAlgorithm.SHA512 to "SHA512"
        )
        repeat(5_000) {
            val name = randomString(random)
            val issuer = if (random.nextInt(8) == 0) "" else randomString(random)
            val (algorithm, algorithmName) = algorithms[random.nextInt(algorithms.size)]
            val digits = random.nextInt(6, 9)
            val period = random.nextInt(1, 121)

            val token = Token(
                name = name,
                issuer = issuer,
                generator = OtpGenerator(
                    secret = byteArrayOf(1, 2, 3, 4),
                    factor = OtpFactor.Timer(period),
                    algorithm = algorithm,
                    digits = digits
                )
            )

            assertEquals(
                SpecReference.toUri(name, issuer, algorithmName, digits, period),
                TokenUrlParser.toUrl(token)
            )
        }
    }

    @Test
    fun `toUrl matches the reference on the spec examples`() {
        val examples = listOf(
            "teeemon@gmail.com" to "Google",
            "teeemon@gmail.com" to "DigitalPlat Domains",
            "+79051533275" to "Госуслуги",
            "na:me" to "Issuer",
            "user" to ""
        )
        for ((name, issuer) in examples) {
            val token = Token(
                name = name,
                issuer = issuer,
                generator = OtpGenerator(
                    secret = byteArrayOf(1, 2, 3, 4),
                    factor = OtpFactor.Timer(30),
                    algorithm = OtpAlgorithm.SHA1,
                    digits = 6
                )
            )
            assertEquals(
                SpecReference.toUri(name, issuer, "SHA1", 6, 30),
                TokenUrlParser.toUrl(token)
            )
        }
    }

    /** Строка из случайных кодовых точек, включая суррогатные пары и «опасные» символы. */
    private fun randomString(random: Random): String = buildString {
        repeat(random.nextInt(0, 12)) {
            when (random.nextInt(6)) {
                0 -> append(" +@:/?&=%#".random(random))
                1 -> append(('a' + random.nextInt(26)))
                2 -> append("-._~".random(random))
                3 -> append((0x400 + random.nextInt(0x50)).toChar())   // кириллица
                4 -> appendCodePoint(0x1F300 + random.nextInt(0x100))  // эмодзи (суррогатная пара)
                else -> appendCodePoint(random.nextInt(0x20, 0x2FFF))
            }
        }
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
        if (codePoint in 0xD800..0xDFFF) return append('?')
        return append(String(Character.toChars(codePoint)))
    }
}
