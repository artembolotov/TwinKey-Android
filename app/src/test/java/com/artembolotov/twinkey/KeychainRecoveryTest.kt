package com.artembolotov.twinkey

import com.artembolotov.twinkey.data.InMemorySecureStore
import com.artembolotov.twinkey.data.KeychainService
import com.artembolotov.twinkey.data.KeychainStatus
import com.artembolotov.twinkey.data.SecureStore
import com.artembolotov.twinkey.data.SecureStoreProvider
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.CharConversionException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException

/**
 * Сценарий: хранилище приехало с бэкапом на новое устройство. Keyset'ы Tink лежат в
 * восстановленном prefs-файле, а master key из Keystore на новом устройстве отсутствует —
 * Tink генерирует новый и не может им расшифровать keyset.
 *
 * Исключения здесь — не догадки. `GeneralSecurityException("decryption failed")` и
 * `InvalidProtocolBufferException` (наследник [IOException]; в tink-android класс shaded —
 * `com.google.crypto.tink.shaded.protobuf`) сняты с настоящего Tink:
 * keyset, записанный `KeysetHandle.write(writer, masterA)`, читается
 * `KeysetHandle.read(reader, masterB)` — ровно тем вызовом, который делает
 * AndroidKeysetManager.java:381 внутри `EncryptedSharedPreferences.create()`.
 * [CharConversionException], [KeyStoreException] и [ProviderException] — из исходников
 * androidx.security-crypto 1.1.0 / tink-android 1.8.0, разбор в KDoc [KeychainService].
 *
 * До фикса `KeychainService` открывал EncryptedSharedPreferences в инициализаторе свойства
 * без обработки ошибок, поэтому любое из них вылетало из конструктора. Конструктор зовётся
 * из инициализатора свойства AccountsViewModel — то есть краш на старте, и повторяемый.
 */
class KeychainRecoveryTest {

    /** Первое открытие падает; после [reset] хранилище открывается пустым. */
    private class FailingOnceProvider(
        private val error: Throwable,
        private val hadData: Boolean = true,
    ) : SecureStoreProvider {
        var openCalls = 0
            private set
        var resetCalls = 0
            private set
        private var wiped = false

        override fun hasStoredData(): Boolean = hadData

        override fun open(): SecureStore {
            openCalls++
            if (!wiped) throw error
            return InMemorySecureStore()
        }

        override fun reset() {
            resetCalls++
            wiped = true
        }
    }

    /** Открыть не удаётся никогда, даже после сноса — деградированный режим. */
    private class AlwaysFailingProvider(private val error: Throwable) : SecureStoreProvider {
        var resetCalls = 0
            private set

        override fun hasStoredData(): Boolean = true
        override fun open(): SecureStore = throw error
        override fun reset() {
            resetCalls++
        }
    }

    /** Исправное хранилище — как на обычном запуске. */
    private class WorkingProvider(private val hadData: Boolean) : SecureStoreProvider {
        val store = InMemorySecureStore()
        var resetCalls = 0
            private set

        override fun hasStoredData(): Boolean = hadData
        override fun open(): SecureStore = store
        override fun reset() {
            resetCalls++
        }
    }

    private fun token(name: String) = Token(
        name = name,
        issuer = "TwinKey",
        generator = OtpGenerator(secret = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    )

    /**
     * Главный сценарий: до фикса конструктор бросал наружу и приложение падало на старте.
     * Проверяем каждое исключение, которое реально может прилететь из
     * `EncryptedSharedPreferences.create()`, включая unchecked-варианты из Keystore —
     * их не покрывает объявленный `throws GeneralSecurityException, IOException`.
     */
    @Test
    fun `open failure never escapes the constructor`() {
        val errors = listOf(
            GeneralSecurityException("decryption failed"),
            IOException("Protocol message contained an invalid tag (zero)."),
            CharConversionException("can't read keyset; the pref value is not a valid hex string"),
            KeyStoreException("the master key android-keystore://x exists but is unusable"),
            ProviderException("Failed to load key from Keystore"),
            IllegalArgumentException("bad base-64"),
            SecurityException("Could not decrypt value."),
        )

        for (error in errors) {
            val name = error::class.java.simpleName
            val provider = FailingOnceProvider(error)

            val keychain = KeychainService(provider)

            assertEquals("$name: хранилище должно быть пересоздано", 1, provider.resetCalls)
            assertEquals("$name: ровно одна повторная попытка", 2, provider.openCalls)
            assertEquals(
                "$name: потеря данных должна быть видна снаружи",
                KeychainStatus.Recovered,
                keychain.status
            )
            assertEquals("$name: пересозданное хранилище пустое", emptyList<Token>(), keychain.loadAll())
        }
    }

    /** После сноса хранилище рабочее: добавленные заново аккаунты сохраняются и читаются. */
    @Test
    fun `recovered keychain is usable again`() {
        val keychain = KeychainService(FailingOnceProvider(GeneralSecurityException("decryption failed")))

        keychain.saveAll(listOf(token("a@twinkey.app")))

        assertEquals(listOf("a@twinkey.app"), keychain.loadAll().map { it.name })
    }

    /**
     * Битое состояние не залипает: следующий запуск — новый KeychainService поверх уже
     * пересозданного хранилища — проходит без сноса и без сообщения о потере.
     */
    @Test
    fun `second launch after recovery is clean`() {
        val provider = WorkingProvider(hadData = true)
        provider.store.putStrings(mapOf("order" to "[]", "accounts" to "{}"))

        val keychain = KeychainService(provider)

        assertEquals(KeychainStatus.Ready, keychain.status)
        assertEquals(0, provider.resetCalls)
        assertEquals(emptyList<Token>(), keychain.loadAll())
    }

    /** Снести не удалось — падать всё равно нельзя, работаем в памяти до перезапуска. */
    @Test
    fun `unrecoverable storage degrades instead of crashing`() {
        val provider = AlwaysFailingProvider(GeneralSecurityException("decryption failed"))

        val keychain = KeychainService(provider)

        assertEquals(KeychainStatus.Unavailable, keychain.status)
        assertEquals("повторять снос бессмысленно", 1, provider.resetCalls)

        // Сессия остаётся работоспособной, просто ничего не переживёт перезапуск.
        keychain.saveAll(listOf(token("a@twinkey.app")))
        assertEquals(listOf("a@twinkey.app"), keychain.loadAll().map { it.name })
    }

    /** Первый запуск: хранилища не было, открыть не вышло — терять было нечего. */
    @Test
    fun `first launch failure is not reported as data loss`() {
        val provider = FailingOnceProvider(GeneralSecurityException("decryption failed"), hadData = false)

        val keychain = KeychainService(provider)

        assertEquals(KeychainStatus.Ready, keychain.status)
        assertFalse(keychain.hadStoredData)
    }

    /**
     * Keyset'ы открылись, а значения — нет: EncryptedSharedPreferences заворачивает
     * ошибку расшифровки в unchecked [SecurityException] уже на getString
     * (EncryptedSharedPreferences.java:595). Читать нечего, но и падать нельзя; мусор
     * вычищаем, чтобы не спотыкаться об него на каждом запуске.
     */
    @Test
    fun `unreadable values are reported and wiped instead of thrown`() {
        val failingReads = object : SecureStore {
            var removed = false
                private set

            override fun getString(key: String): String? =
                throw SecurityException("Could not decrypt value.")

            override fun putStrings(values: Map<String, String>) = Unit

            override fun remove(keys: List<String>) {
                removed = true
            }
        }
        val provider = object : SecureStoreProvider {
            override fun hasStoredData(): Boolean = true
            override fun open(): SecureStore = failingReads
            override fun reset() = Unit
        }

        val keychain = KeychainService(provider)

        assertEquals(emptyList<Token>(), keychain.loadAll())
        assertEquals(KeychainStatus.Recovered, keychain.status)
        assertTrue("битые значения должны быть удалены", failingReads.removed)
    }

    /** Битый JSON в расшифрованных значениях — тоже не повод падать. */
    @Test
    fun `corrupted json is reported and wiped instead of thrown`() {
        val provider = WorkingProvider(hadData = true)
        provider.store.putStrings(mapOf("order" to "not json", "accounts" to "{}"))

        val keychain = KeychainService(provider)

        assertEquals(emptyList<Token>(), keychain.loadAll())
        assertEquals(KeychainStatus.Recovered, keychain.status)
        assertNull(provider.store.getString("order"))
    }
}
