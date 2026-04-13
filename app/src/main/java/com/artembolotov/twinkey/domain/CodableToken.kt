package com.artembolotov.twinkey.domain

import kotlinx.serialization.Serializable

// Порт Token.CodableModel из iOS.
// Формат совместим с iOS backup-файлами .twinkey.
// url — otpauth:// URL без секрета (как в iOS urlForToken).
// secret — Base64-кодированные байты секрета (Swift Codable кодирует Data как Base64).
@Serializable
data class CodableToken(
    val url: String,
    val secret: String
)
