package com.artembolotov.twinkey

import com.artembolotov.twinkey.data.BackupManager
import com.artembolotov.twinkey.data.SkipReason
import com.artembolotov.twinkey.domain.OtpAlgorithm
import com.artembolotov.twinkey.domain.OtpFactor
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TokenUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты единой спецификации бэкапа — docs/BACKUP_FORMAT.md.
 *
 * Проверяют две вещи:
 *  1) запись строго канонична (§2, §3) — байты должны совпадать с iOS;
 *  2) чтение снисходительно (§4) — старые бэкапы iOS и Android продолжают открываться.
 *
 * Фикстуры legacy_ios.twinkey / legacy_android.twinkey в src/test/resources —
 * это реальные старые файлы обеих платформ (с синтетическими секретами):
 * те же экранированные слэши, form-encoding, "+" вместо пробела.
 */
class BackupFormatTest {

    private fun token(
        name: String,
        issuer: String,
        secret: ByteArray = "12345678901234567890".toByteArray(Charsets.US_ASCII),
        algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
        digits: Int = 6,
        period: Int = 30
    ) = Token(
        name = name,
        issuer = issuer,
        generator = OtpGenerator(
            secret = secret,
            factor = OtpFactor.Timer(period),
            algorithm = algorithm,
            digits = digits
        )
    )

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "fixture $name not found" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    // ---- §3.1 percent-encoding ----

    @Test
    fun `percentEncode follows RFC 3986, not form encoding`() {
        assertEquals("DigitalPlat%20Domains", TokenUrlParser.percentEncode("DigitalPlat Domains"))
        assertEquals("%2B79051533275", TokenUrlParser.percentEncode("+79051533275"))
        assertEquals("user%40example.com", TokenUrlParser.percentEncode("user@example.com"))
        assertEquals("na%3Ame", TokenUrlParser.percentEncode("na:me"))
        assertEquals("azAZ09-._~", TokenUrlParser.percentEncode("azAZ09-._~"))
        // UTF-8, шестнадцатеричные цифры в верхнем регистре
        assertEquals(
            "%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8",
            TokenUrlParser.percentEncode("Госуслуги")
        )
    }

    @Test
    fun `percentDecode keeps plus as plus`() {
        assertEquals("+79051533275", TokenUrlParser.percentDecode("+79051533275"))
        assertEquals("DigitalPlat+Domains", TokenUrlParser.percentDecode("DigitalPlat+Domains"))
        assertEquals("DigitalPlat Domains", TokenUrlParser.percentDecode("DigitalPlat%20Domains"))
        assertEquals("Госуслуги", TokenUrlParser.percentDecode("%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8"))
        // Незавершённые/битые последовательности не должны ронять чтение
        assertEquals("100%", TokenUrlParser.percentDecode("100%"))
        assertEquals("a%zz", TokenUrlParser.percentDecode("a%zz"))
    }

    // ---- §3.2 таблица примеров ----

    @Test
    fun `toUrl matches the examples in the spec`() {
        assertEquals(
            "otpauth://totp/Google:teeemon%40gmail.com" +
                    "?algorithm=SHA1&digits=6&issuer=Google&period=30",
            TokenUrlParser.toUrl(token(name = "teeemon@gmail.com", issuer = "Google"))
        )
        assertEquals(
            "otpauth://totp/DigitalPlat%20Domains:teeemon%40gmail.com" +
                    "?algorithm=SHA1&digits=6&issuer=DigitalPlat%20Domains&period=30",
            TokenUrlParser.toUrl(token(name = "teeemon@gmail.com", issuer = "DigitalPlat Domains"))
        )
        assertEquals(
            "otpauth://totp/%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8:%2B79051533275" +
                    "?algorithm=SHA1&digits=6" +
                    "&issuer=%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8&period=30",
            TokenUrlParser.toUrl(token(name = "+79051533275", issuer = "Госуслуги"))
        )
        assertEquals(
            "otpauth://totp/Issuer:na%3Ame?algorithm=SHA1&digits=6&issuer=Issuer&period=30",
            TokenUrlParser.toUrl(token(name = "na:me", issuer = "Issuer"))
        )
        assertEquals(
            "otpauth://totp/user?algorithm=SHA1&digits=6&issuer=&period=30",
            TokenUrlParser.toUrl(token(name = "user", issuer = ""))
        )
    }

    @Test
    fun `toUrl keeps query item order and writes non-default values`() {
        val url = TokenUrlParser.toUrl(
            token(name = "u", issuer = "I", algorithm = OtpAlgorithm.SHA512, digits = 8, period = 60)
        )
        assertEquals("otpauth://totp/I:u?algorithm=SHA512&digits=8&issuer=I&period=60", url)
    }

    // ---- §2 файл ----

    @Test
    fun `export writes compact JSON with url before secret and unescaped slashes`() {
        val json = BackupManager.export(
            listOf(
                token(name = "teeemon@gmail.com", issuer = "Google", secret = byteArrayOf(0, 1, 2, 3)),
                token(name = "+79051533275", issuer = "Госуслуги", secret = byteArrayOf(4, 5, 6, 7))
            )
        )

        assertEquals(
            """[{"url":"otpauth://totp/Google:teeemon%40gmail.com?algorithm=SHA1&digits=6&issuer=Google&period=30","secret":"AAECAw=="},""" +
                    """{"url":"otpauth://totp/%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8:%2B79051533275?algorithm=SHA1&digits=6&issuer=%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8&period=30","secret":"BAUGBw=="}]""",
            json
        )
        assertFalse("solidus must not be escaped", json.contains("\\/"))
        assertFalse("backup must not contain the secret in the URL", json.contains("secret="))
        // §2.6: канонический бэкап — чистый ASCII, без \\uXXXX
        assertTrue(json.all { it.code in 0x20..0x7E })
    }

    @Test
    fun `export never writes HOTP entries`() {
        val hotp = Token(
            name = "bob",
            issuer = "Acme",
            generator = OtpGenerator(
                secret = byteArrayOf(0, 1, 2, 3),
                factor = OtpFactor.Counter(7),
                algorithm = OtpAlgorithm.SHA1,
                digits = 6
            )
        )

        val json = BackupManager.export(listOf(hotp, token(name = "alice", issuer = "Acme")))

        assertFalse(json.contains("hotp"))
        assertFalse(json.contains("counter"))
        assertEquals(listOf("alice"), BackupManager.import(json).successful.map { it.name })
    }

    @Test
    fun `export import roundtrip preserves accounts`() {
        val tokens = listOf(
            token(name = "teeemon@gmail.com", issuer = "DigitalPlat Domains"),
            token(name = "+79051533275", issuer = "Госуслуги"),
            token(name = "na:me", issuer = "Issuer"),
            token(name = "@somebody", issuer = "Twitter"),
            token(name = "lonely", issuer = ""),
            token(name = "8 digits", issuer = "SHA512 Inc", algorithm = OtpAlgorithm.SHA512, digits = 8, period = 60)
        )

        val restored = BackupManager.import(BackupManager.export(tokens)).successful

        assertEquals(tokens.map { it.name }, restored.map { it.name })
        assertEquals(tokens.map { it.issuer }, restored.map { it.issuer })
        assertEquals(tokens.map { it.generator.digits }, restored.map { it.generator.digits })
        assertEquals(tokens.map { it.generator.algorithm }, restored.map { it.generator.algorithm })
        assertEquals(tokens.map { it.generator.factor }, restored.map { it.generator.factor })
        tokens.zip(restored).forEach { (a, b) ->
            assertTrue(a.generator.secret.contentEquals(b.generator.secret))
        }
    }

    // ---- §4 чтение старых файлов ----

    @Test
    fun `legacy iOS backup still imports`() {
        val result = BackupManager.import(readFixture("legacy_ios.twinkey"))

        assertTrue(result.skipped.isEmpty())
        assertEquals(
            listOf("u61786", "user@example.com", "+79051533275", "@somebody", "lonely"),
            result.successful.map { it.name }
        )
        assertEquals(
            listOf("netangels.ru", "DigitalPlat Domains", "Госуслуги", "Twitter", ""),
            result.successful.map { it.issuer }
        )
    }

    @Test
    fun `legacy Android backup still imports`() {
        val result = BackupManager.import(readFixture("legacy_android.twinkey"))

        assertTrue(result.skipped.isEmpty())
        assertEquals(
            listOf("u61786", "user@example.com", "+79051533275", "@somebody", "lonely"),
            result.successful.map { it.name }
        )
        // §5: пробел в issuer был записан как '+' и неотличим от настоящего плюса —
        // читатель не угадывает и оставляет плюс как есть
        assertEquals(
            listOf("netangels.ru", "DigitalPlat+Domains", "Госуслуги", "Twitter", ""),
            result.successful.map { it.issuer }
        )
    }

    @Test
    fun `both legacy backups carry the same accounts and secrets`() {
        val ios = BackupManager.import(readFixture("legacy_ios.twinkey")).successful
        val android = BackupManager.import(readFixture("legacy_android.twinkey")).successful

        assertEquals(ios.map { it.name }, android.map { it.name })
        ios.zip(android).forEach { (a, b) ->
            assertTrue(a.generator.secret.contentEquals(b.generator.secret))
        }
    }

    @Test
    fun `re-exported legacy backups are byte-identical apart from the plus quirk`() {
        val ios = BackupManager.import(readFixture("legacy_ios.twinkey")).successful
        val android = BackupManager.import(readFixture("legacy_android.twinkey")).successful

        // Оба файла после импорта пишутся в одной канонической форме;
        // расходится только issuer со сломанным пробелом (§5).
        val fixed = android.map {
            if (it.issuer == "DigitalPlat+Domains") it.copy(issuer = "DigitalPlat Domains") else it
        }
        assertEquals(BackupManager.export(ios), BackupManager.export(fixed))
    }

    @Test
    fun `canonical label with an unencoded colon wins over the issuer item`() {
        // Двоеточие внутри имени закодировано, разделитель — нет
        val token = TokenUrlParser.parse(
            "otpauth://totp/Issuer:na%3Ame?secret=JBSWY3DPEHPK3PXP&algorithm=SHA1&digits=6&issuer=Issuer&period=30"
        )
        assertEquals("na:me", token.name)
        assertEquals("Issuer", token.issuer)
    }

    @Test
    fun `legacy label decoded and split by the issuer prefix`() {
        // Старый Android: весь label form-encoded, двоеточие ушло в %3A
        val token = TokenUrlParser.parse(
            "otpauth://totp/GitHub%3Aalice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        )
        assertEquals("alice", token.name)
        assertEquals("GitHub", token.issuer)
    }

    @Test
    fun `missing query items fall back to SHA1 6 30`() {
        val token = TokenUrlParser.parse("otpauth://totp/Acme:bob?secret=JBSWY3DPEHPK3PXP")
        assertEquals(OtpAlgorithm.SHA1, token.generator.algorithm)
        assertEquals(6, token.generator.digits)
        assertEquals(OtpFactor.Timer(30), token.generator.factor)
    }

    @Test
    fun `HOTP entries are skipped on import`() {
        val json = """[{"url":"otpauth://hotp/Acme:bob?algorithm=SHA1&digits=6&issuer=Acme&counter=0","secret":"AAECAw=="},""" +
                """{"url":"otpauth://totp/Acme:alice?algorithm=SHA1&digits=6&issuer=Acme&period=30","secret":"AAECAw=="}]"""

        val result = BackupManager.import(json)

        assertEquals(listOf("alice"), result.successful.map { it.name })
        assertEquals(listOf("Acme"), result.skipped.map { it.name })
        assertEquals(SkipReason.UnsupportedType("HOTP"), result.skipped.single().reason)
    }

    @Test
    fun `entries with a broken secret are skipped, the rest import`() {
        val json = """[{"url":"otpauth://totp/Acme:bob?algorithm=SHA1&digits=6&issuer=Acme&period=30","secret":"!!not base64!!"},""" +
                """{"url":"otpauth://totp/Acme:alice?algorithm=SHA1&digits=6&issuer=Acme&period=30","secret":"AAECAw=="}]"""

        val result = BackupManager.import(json)

        assertEquals(listOf("alice"), result.successful.map { it.name })
        assertEquals(listOf("bob"), result.skipped.map { it.name })
        assertEquals(SkipReason.InvalidAccount, result.skipped.single().reason)
    }
}
