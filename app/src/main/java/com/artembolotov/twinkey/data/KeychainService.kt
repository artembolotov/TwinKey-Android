package com.artembolotov.twinkey.data

import android.content.Context
import android.util.Log
import com.artembolotov.twinkey.domain.CodableToken
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TokenUrlParser
import kotlinx.serialization.json.Json

/** Чем закончилась инициализация хранилища. */
enum class KeychainStatus {
    /** Открылось нормально (либо создано пустым при первом запуске). */
    Ready,

    /** Хранилище было, но расшифровать его не удалось: пересоздано пустым, старые аккаунты потеряны. */
    Recovered,

    /** Не удалось даже пересоздать. Работаем в памяти: ничего не сохраняется до перезапуска. */
    Unavailable,
}

/**
 * Порт KeychainService.swift.
 *
 * Хранит аккаунты в EncryptedSharedPreferences (Android Keystore = аналог iOS Keychain).
 * Ключи совпадают с iOS для концептуальной совместимости:
 *   "order"    — JSON-массив UUID-строк (порядок аккаунтов)
 *   "accounts" — JSON-объект { UUID: CodableToken }
 *
 * ## Почему всё обёрнуто в try/catch
 *
 * Открытие хранилища может провалиться необратимо — типовой случай: файл prefs приехал
 * с бэкапом на новое устройство, а master key из Keystore неэкспортируем и там не появился.
 * Тогда Tink находит keyset, генерирует новый master key и не может им расшифровать keyset.
 * Проверено на androidx.security-crypto 1.1.0 / tink-android 1.8.0, летит:
 *
 *  - `java.security.GeneralSecurityException: decryption failed` — основной случай
 *    (AndroidKeysetManager.java:381 → :397: cleartext-fallback тоже падает, и Tink
 *    перебрасывает исходное исключение);
 *  - `com.google.crypto.tink.shaded.protobuf.InvalidProtocolBufferException` (наследник
 *    `IOException`) — если keyset-блоб испорчен, а не зашифрован чужим ключом. Пакет
 *    именно shaded: tink-android прячет в себя protobuf, у нешейженного tink класс
 *    лежит в `com.google.protobuf`;
 *  - `java.io.CharConversionException` — если значение keyset'а не разбирается как hex
 *    (AndroidKeysetManager.java:260);
 *  - `java.security.KeyStoreException` — master key есть, но непригоден (там же, :331).
 *
 * Первые два воспроизведены на настоящем Tink, остальные два — из его исходников. Второй
 * дополнительно снят с устройства: порча keyset'а в prefs роняет `create()` ровно им.
 * Кроме них есть непроверяемые компилятором `ProviderException` из Keystore и
 * `java.lang.SecurityException`, в который EncryptedSharedPreferences заворачивает
 * ошибку расшифровки уже при чтении значения (EncryptedSharedPreferences.java:595) —
 * то есть упасть может не только открытие, но и [loadAll]/[saveAll]. Поэтому ловим
 * `Exception`, а не конкретный тип: список заведомо неполный и зависит от версии Tink.
 */
class KeychainService internal constructor(private val provider: SecureStoreProvider) {

    constructor(context: Context) : this(EncryptedPrefsStoreProvider(context))

    /** Были ли данные на диске до открытия — отличает первый запуск от переезда на новое устройство. */
    val hadStoredData: Boolean = runCatching { provider.hasStoredData() }.getOrDefault(false)

    var status: KeychainStatus = KeychainStatus.Ready
        private set

    private val store: SecureStore = openStore()

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TAG = "KeychainService"
        const val ORDER_KEY = "order"
        const val ACCOUNTS_KEY = "accounts"
    }

    // --- Init ---

    /**
     * Никогда не бросает: конструктор вызывается из инициализатора свойства
     * AccountsViewModel, и исключение отсюда означало бы повторяемый краш на старте.
     */
    private fun openStore(): SecureStore {
        try {
            return provider.open()
        } catch (e: Exception) {
            Log.w(TAG, "Хранилище не открылось, пересоздаём с нуля", e)
        }

        // Расшифровать нечем, чинить нечего — сносим файл вместе с master key и
        // создаём пустое хранилище. Одна попытка: если и она не прошла, повторять
        // бессмысленно, состояние уже не про испорченные данные.
        return try {
            provider.reset()
            provider.open().also {
                if (hadStoredData) status = KeychainStatus.Recovered
            }
        } catch (e: Exception) {
            Log.e(TAG, "Хранилище недоступно, работаем в памяти до перезапуска", e)
            status = KeychainStatus.Unavailable
            InMemorySecureStore()
        }
    }

    // --- Load ---

    fun loadAll(): List<Token> = try {
        val orderJson = store.getString(ORDER_KEY)
        val accountsJson = store.getString(ACCOUNTS_KEY)

        if (orderJson == null || accountsJson == null) {
            emptyList()
        } else {
            val ids = json.decodeFromString<List<String>>(orderJson)
            val data = json.decodeFromString<Map<String, CodableToken>>(accountsJson)

            ids.mapNotNull { id ->
                data[id]?.let { codable ->
                    runCatching { TokenUrlParser.fromCodableToken(codable, id) }
                        .onFailure { Log.w(TAG, "Skipping unparseable token id=$id", it) }
                        .getOrNull()
                }
            }
        }
    } catch (e: Exception) {
        // Keyset'ы открылись, а значения — нет (SecurityException из
        // EncryptedSharedPreferences) или в них не JSON. Прочитать уже нечего;
        // вычищаем, чтобы не спотыкаться об это на каждом запуске.
        Log.e(TAG, "Не удалось прочитать аккаунты, очищаем хранилище", e)
        if (status == KeychainStatus.Ready) status = KeychainStatus.Recovered
        clear()
        emptyList()
    }

    // --- Save ---

    fun saveAll(tokens: List<Token>) {
        val ids = tokens.map { it.id }
        val data = tokens.associate { token ->
            token.id to TokenUrlParser.toCodableToken(token)
        }
        runCatching {
            store.putStrings(
                mapOf(
                    ORDER_KEY to json.encodeToString(ids),
                    ACCOUNTS_KEY to json.encodeToString(data)
                )
            )
        }.onFailure { Log.e(TAG, "Не удалось сохранить аккаунты", it) }
    }

    // --- Clear ---

    fun clear() {
        runCatching { store.remove(listOf(ORDER_KEY, ACCOUNTS_KEY)) }
            .onFailure { Log.e(TAG, "Не удалось очистить хранилище", it) }
    }
}
