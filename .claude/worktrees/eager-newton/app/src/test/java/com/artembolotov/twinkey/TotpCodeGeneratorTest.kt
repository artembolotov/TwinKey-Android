package com.artembolotov.twinkey

import com.artembolotov.twinkey.domain.OtpAlgorithm
import com.artembolotov.twinkey.domain.OtpFactor
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.TotpCodeGenerator
import com.artembolotov.twinkey.domain.TokenUrlParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты порта Generator.swift.
 *
 * Тест-векторы взяты из официальных RFC:
 *   RFC 4226 Appendix D — HOTP
 *   RFC 6238 Appendix B — TOTP (SHA1, SHA256, SHA512)
 */
class TotpCodeGeneratorTest {

    // ---- RFC 4226 Appendix D — HOTP (counter-based) ----

    @Test
    fun `RFC 4226 HOTP test vectors`() {
        // Секрет из RFC 4226: ASCII "12345678901234567890"
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        // Ожидаемые 6-значные коды для counter 0..9
        val expected = listOf("755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489")

        expected.forEachIndexed { counter, expectedCode ->
            val generator = OtpGenerator(
                secret = secret,
                factor = OtpFactor.Counter(counter.toLong()),
                algorithm = OtpAlgorithm.SHA1,
                digits = 6
            )
            val code = TotpCodeGenerator.generateCode(generator, timestampMs = 0L)
            assertEquals("counter=$counter", expectedCode, code)
        }
    }

    // ---- RFC 6238 Appendix B — TOTP (8-значные коды) ----

    @Test
    fun `RFC 6238 TOTP SHA1 at T=59s`() {
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val generator = OtpGenerator(
            secret = secret,
            factor = OtpFactor.Timer(period = 30),
            algorithm = OtpAlgorithm.SHA1,
            digits = 8
        )
        // T=59s → counter=1, RFC 6238 ожидает: 94287082
        val code = TotpCodeGenerator.generateCode(generator, timestampMs = 59_000L)
        assertEquals("94287082", code)
    }

    @Test
    fun `RFC 6238 TOTP SHA1 at T=1111111109s`() {
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val generator = OtpGenerator(
            secret = secret,
            factor = OtpFactor.Timer(period = 30),
            algorithm = OtpAlgorithm.SHA1,
            digits = 8
        )
        // T=1111111109s → counter=37037036, RFC 6238 ожидает: 07081804
        val code = TotpCodeGenerator.generateCode(generator, timestampMs = 1_111_111_109_000L)
        assertEquals("07081804", code)
    }

    @Test
    fun `RFC 6238 TOTP SHA256 at T=59s`() {
        val secret = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
        val generator = OtpGenerator(
            secret = secret,
            factor = OtpFactor.Timer(period = 30),
            algorithm = OtpAlgorithm.SHA256,
            digits = 8
        )
        // RFC 6238 ожидает: 46119246
        val code = TotpCodeGenerator.generateCode(generator, timestampMs = 59_000L)
        assertEquals("46119246", code)
    }

    @Test
    fun `RFC 6238 TOTP SHA512 at T=59s`() {
        val secret = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)
        val generator = OtpGenerator(
            secret = secret,
            factor = OtpFactor.Timer(period = 30),
            algorithm = OtpAlgorithm.SHA512,
            digits = 8
        )
        // RFC 6238 ожидает: 90693936
        val code = TotpCodeGenerator.generateCode(generator, timestampMs = 59_000L)
        assertEquals("90693936", code)
    }

    // ---- secondsRemaining ----

    @Test
    fun `secondsRemaining basic cases`() {
        // Прошло 45с из 30с периода → остаток = 30 - (45 % 30) = 30 - 15 = 15
        assertEquals(15, TotpCodeGenerator.secondsRemaining(30, 45_000L))
        // Начало нового периода (60с) → остаток = 30
        assertEquals(30, TotpCodeGenerator.secondsRemaining(30, 60_000L))
        // Последняя секунда периода (59с) → 60 - 59 = 1
        assertEquals(1, TotpCodeGenerator.secondsRemaining(30, 59_000L))
    }

    // ---- TokenUrlParser ----

    @Test
    fun `parse standard otpauth TOTP URL`() {
        val url = "otpauth://totp/GitHub:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30"
        val token = TokenUrlParser.parse(url)

        assertEquals("user@example.com", token.name)
        assertEquals("GitHub", token.issuer)
        assertEquals(OtpAlgorithm.SHA1, token.generator.algorithm)
        assertEquals(6, token.generator.digits)
        assertEquals(OtpFactor.Timer(30), token.generator.factor)
    }

    @Test
    fun `parse URL without issuer uses prefix from name`() {
        val url = "otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP"
        val token = TokenUrlParser.parse(url)
        assertEquals("alice", token.name)
        assertEquals("GitHub", token.issuer)
    }

    @Test
    fun `parse URL with no issuer prefix`() {
        val url = "otpauth://totp/alice?secret=JBSWY3DPEHPK3PXP"
        val token = TokenUrlParser.parse(url)
        assertEquals("alice", token.name)
        assertEquals("", token.issuer)
    }

    @Test
    fun `toUrl and parse roundtrip`() {
        val original = com.artembolotov.twinkey.domain.Token(
            id = "test-id",
            name = "user@example.com",
            issuer = "Google",
            generator = OtpGenerator(
                secret = "JBSWY3DPEHPK3PXP".toByteArray(),
                factor = OtpFactor.Timer(30),
                algorithm = OtpAlgorithm.SHA1,
                digits = 6
            )
        )
        // Сохраняем в CodableToken и восстанавливаем
        val codable = TokenUrlParser.toCodableToken(original)
        val restored = TokenUrlParser.fromCodableToken(codable, "test-id")

        assertEquals(original.name, restored.name)
        assertEquals(original.issuer, restored.issuer)
        assertEquals(original.generator.factor, restored.generator.factor)
        assertEquals(original.generator.algorithm, restored.generator.algorithm)
        assertEquals(original.generator.digits, restored.generator.digits)
    }
}
