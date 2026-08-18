package com.artembolotov.twinkey

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artembolotov.twinkey.data.EncryptedPrefsStoreProvider
import com.artembolotov.twinkey.data.KeychainService
import com.artembolotov.twinkey.data.KeychainStatus
import com.artembolotov.twinkey.domain.OtpGenerator
import com.artembolotov.twinkey.domain.Token
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * То же, что [KeychainRecoveryTest], но на настоящих EncryptedSharedPreferences и настоящем
 * Android Keystore — здесь проверяется, что восстановление действительно работает на
 * устройстве, а не только против подставного провайдера.
 *
 * Переезд на новое устройство имитируется точно: prefs-файл с keyset'ами Tink остаётся,
 * а master key из Keystore удаляется — ровно то состояние, в которое приводит восстановление
 * бэкапа, потому что ключ Keystore неэкспортируем и на новом устройстве его нет.
 *
 * Хранилище одноразовое, боевое (`ru.artembolotov.twinkey.otp` + `_androidx_security_master_key_`)
 * тест не трогает: запуск на личном устройстве не удалит аккаунты.
 */
@RunWith(AndroidJUnit4::class)
class KeychainRecoveryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun provider() = EncryptedPrefsStoreProvider(context, PREFS_NAME, MASTER_KEY_ALIAS)

    private fun token(name: String) = Token(
        name = name,
        issuer = "TwinKey",
        generator = OtpGenerator(secret = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    )

    @Before
    fun clean() = provider().reset()

    @After
    fun cleanUp() = provider().reset()

    /** Удаляет только master key: keyset'ы Tink остаются в prefs и расшифровать их станет нечем. */
    private fun simulateRestoreOnAnotherDevice() {
        KeyStore.getInstance("AndroidKeyStore")
            .apply { load(null) }
            .deleteEntry(MASTER_KEY_ALIAS)
    }

    @Test
    fun survivesRestoreFromAnotherDevice() {
        KeychainService(provider()).saveAll(listOf(token("a@twinkey.app")))

        simulateRestoreOnAnotherDevice()

        // Критерий 2: не падает, потеря данных видна снаружи — приложение может объяснить
        // её пользователю и предложить импорт .twinkey.
        val afterRestore = KeychainService(provider())
        assertEquals(KeychainStatus.Recovered, afterRestore.status)
        assertEquals(emptyList<Token>(), afterRestore.loadAll())

        // Хранилище снова рабочее: импорт из .twinkey сохранится.
        afterRestore.saveAll(listOf(token("b@twinkey.app")))
        assertEquals(listOf("b@twinkey.app"), afterRestore.loadAll().map { it.name })

        // Критерий 3: битое состояние не залипает — следующий запуск обычный.
        val nextLaunch = KeychainService(provider())
        assertEquals(KeychainStatus.Ready, nextLaunch.status)
        assertEquals(listOf("b@twinkey.app"), nextLaunch.loadAll().map { it.name })
    }

    /** Мусор вместо keyset'а — второй способ получить нерасшифровываемое состояние. */
    @Test
    fun survivesCorruptedKeyset() {
        KeychainService(provider()).saveAll(listOf(token("a@twinkey.app")))

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString("__androidx_security_crypto_encrypted_prefs_value_keyset__", "not a keyset")
        }

        val afterCorruption = KeychainService(provider())
        assertEquals(KeychainStatus.Recovered, afterCorruption.status)
        assertEquals(emptyList<Token>(), afterCorruption.loadAll())

        assertEquals(KeychainStatus.Ready, KeychainService(provider()).status)
    }

    private companion object {
        const val PREFS_NAME = "twinkey_recovery_instrumented_test"
        const val MASTER_KEY_ALIAS = "twinkey_recovery_instrumented_test_master_key"
    }
}
