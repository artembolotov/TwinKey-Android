@file:Suppress("DEPRECATION")

package com.artembolotov.twinkey.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * Плоское строковое key-value хранилище — минимум, который нужен [KeychainService].
 *
 * Отдельный интерфейс нужен, чтобы логику восстановления после нерасшифровываемого
 * состояния можно было проверять обычным JVM-тестом: настоящая реализация тянет за собой
 * Android Keystore, которого в unit-тестах нет.
 */
interface SecureStore {
    fun getString(key: String): String?
    fun putStrings(values: Map<String, String>)
    fun remove(keys: List<String>)
}

/** Открывает [SecureStore] и умеет сносить его, если открыть не получилось. */
interface SecureStoreProvider {
    /** Есть ли уже данные на диске — проверяется до [open], иначе открытие само создаст файл. */
    fun hasStoredData(): Boolean

    /** Бросает, если хранилище есть, но расшифровать его нечем. */
    fun open(): SecureStore

    /** Удаляет зашифрованный файл и master key: только так можно уйти от нерасшифровываемого состояния. */
    fun reset()
}

/** Хранилище на время сессии — деградированный режим, когда Keystore недоступен совсем. */
class InMemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putStrings(values: Map<String, String>) {
        this.values.putAll(values)
    }

    override fun remove(keys: List<String>) {
        keys.forEach { values.remove(it) }
    }
}

/**
 * Боевая реализация: EncryptedSharedPreferences поверх Android Keystore.
 *
 * Имя файла по умолчанию совпадает с iOS service-name; файл исключён из бэкапа
 * (res/xml/backup_rules.xml, res/xml/data_extraction_rules.xml), потому что master key
 * из Keystore неэкспортируем и на новом устройстве восстановленный файл всё равно
 * не расшифровать.
 *
 * [prefsName] и [masterKeyAlias] переопределяются только в инструментальных тестах: они
 * проверяют настоящий Keystore, и им нужно одноразовое хранилище, чтобы не снести боевое.
 */
class EncryptedPrefsStoreProvider(
    context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME,
    private val masterKeyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS,
) : SecureStoreProvider {

    private val appContext = context.applicationContext

    override fun hasStoredData(): Boolean =
        runCatching { prefsFile().exists() }.getOrDefault(false)

    override fun open(): SecureStore {
        val masterKey = MasterKey.Builder(appContext, masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            appContext,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return SharedPreferencesSecureStore(prefs)
    }

    override fun reset() {
        // Порядок важен: сначала данные (в них лежат Tink-keyset'ы), потом сам ключ.
        // Если снести ключ первым и упасть на удалении файла, следующий запуск получит
        // ровно то же нерасшифровываемое состояние.
        appContext.deleteSharedPreferences(prefsName)
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
                .deleteEntry(masterKeyAlias)
        }.onFailure { Log.w(TAG, "Не удалось удалить master key $masterKeyAlias", it) }
    }

    private fun prefsFile(): File = File(File(appContext.dataDir, "shared_prefs"), "$prefsName.xml")

    companion object {
        private const val TAG = "SecureStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Совпадает с iOS service-name. */
        const val DEFAULT_PREFS_NAME = "ru.artembolotov.twinkey.otp"
    }
}

private class SharedPreferencesSecureStore(private val prefs: SharedPreferences) : SecureStore {

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putStrings(values: Map<String, String>) {
        prefs.edit { values.forEach { (key, value) -> putString(key, value) } }
    }

    override fun remove(keys: List<String>) {
        prefs.edit { keys.forEach { remove(it) } }
    }
}
