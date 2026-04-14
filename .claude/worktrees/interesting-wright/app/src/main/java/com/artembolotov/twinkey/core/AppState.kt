package com.artembolotov.twinkey.core

import com.artembolotov.twinkey.domain.Token

data class AppState(
    val mode: AppMode = AppMode.Unknown,
    val accounts: List<Token> = emptyList(),
    val message: String? = null
)

enum class AppMode { Unknown, Welcome, Accounts }
