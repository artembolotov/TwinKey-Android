package com.artembolotov.twinkey.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.artembolotov.twinkey.domain.CodableToken
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TokenUrlParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Порт KeychainService.swift.
 *
 * Хранит аккаунты в EncryptedSharedPreferences (Android Keystore = аналог iOS Keychain).
 * Ключи совпадают с iOS для концептуальной совместимости:
 *   "order"    — JSON-массив UUID-строк (порядок аккаунтов)
 *   "accounts" — JSON-объект { UUID: CodableToken }
 */
class KeychainService(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ru.artembolotov.twinkey.otp", // имя совпадает с iOS service-name
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val ORDER_KEY = "order"
        const val ACCOUNTS_KEY = "accounts"
    }

    // MARK: - Load

    fun loadAll(): List<Token> {
        val orderJson = prefs.getString(ORDER_KEY, null) ?: return emptyList()
        val accountsJson = prefs.getString(ACCOUNTS_KEY, null) ?: return emptyList()

        val ids = json.decodeFromString<List<String>>(orderJson)
        val data = json.decodeFromString<Map<String, CodableToken>>(accountsJson)

        return ids.mapNotNull { id ->
            data[id]?.let { codable ->
                runCatching { TokenUrlParser.fromCodableToken(codable, id) }.getOrNull()
            }
        }
    }

    // MARK: - Save

    fun saveAll(tokens: List<Token>) {
        val ids = tokens.map { it.id }
        val data = tokens.associate { token ->
            token.id to TokenUrlParser.toCodableToken(token)
        }
        prefs.edit()
            .putString(ORDER_KEY, json.encodeToString(ids))
            .putString(ACCOUNTS_KEY, json.encodeToString(data))
            .apply()
    }

    // MARK: - Clear

    fun clear() {
        prefs.edit().remove(ORDER_KEY).remove(ACCOUNTS_KEY).apply()
    }
}
