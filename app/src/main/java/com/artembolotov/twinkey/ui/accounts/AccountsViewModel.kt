package com.artembolotov.twinkey.ui.accounts

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artembolotov.twinkey.core.AppMode
import com.artembolotov.twinkey.data.AccountRepository
import com.artembolotov.twinkey.data.KeychainService
import com.artembolotov.twinkey.domain.OtpFactor
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TotpCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AccountsOverlay {
    object None : AccountsOverlay()
    object Scanner : AccountsOverlay()
    object Manual : AccountsOverlay()
    data class Added(val token: Token) : AccountsOverlay()
    data class Editing(val token: Token) : AccountsOverlay()
    object Settings : AccountsOverlay()
    object ImportFromEmpty : AccountsOverlay()
}

// Порт AppState + TimerService из iOS
class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val mode: AppMode = AppMode.Unknown,
        val accounts: List<Token> = emptyList(),
        val codes: Map<String, String> = emptyMap(),
        val secondsRemaining: Map<String, Int> = emptyMap(),
        val message: String? = null,
        val welcomeSessionId: Int = 0,
        val overlay: AccountsOverlay = AccountsOverlay.None,
        val editMode: Boolean = false,
        val searchQuery: String = ""
    )

    private val keychain = KeychainService(application)
    private val repository = AccountRepository(keychain)
    private val settings = application.getSharedPreferences("twinkey_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val accounts = repository.loadAll()
        val isFirstLaunch = !settings.contains("initialized")
        val mode = if (isFirstLaunch) AppMode.Welcome else AppMode.Accounts

        _state.update { it.copy(mode = mode, accounts = accounts) }
        updateCodes()

        viewModelScope.launch {
            tickerFlow().collect { updateCodes() }
        }
    }

    // MARK: - Overlay

    fun showOverlay(overlay: AccountsOverlay) = _state.update { it.copy(overlay = overlay) }
    fun dismissOverlay() = _state.update { it.copy(overlay = AccountsOverlay.None) }

    // MARK: - Edit mode / Search

    fun setEditMode(enabled: Boolean) = _state.update { it.copy(editMode = enabled) }
    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    // MARK: - Accounts (порт AccountAction)

    fun addAccount(token: Token) {
        val updated = repository.add(token, _state.value.accounts)
        _state.update { it.copy(accounts = updated, mode = AppMode.Accounts) }
        updateCodes()
    }

    fun deleteAccount(id: String) {
        val updated = repository.delete(id, _state.value.accounts)
        _state.update { it.copy(accounts = updated, editMode = if (updated.isEmpty()) false else it.editMode) }
    }

    fun updateAccount(token: Token) {
        val updated = repository.update(token, _state.value.accounts)
        _state.update { it.copy(accounts = updated) }
    }

    fun moveAccount(fromIndex: Int, toIndex: Int) {
        val updated = repository.move(fromIndex, toIndex, _state.value.accounts)
        _state.update { it.copy(accounts = updated) }
    }

    fun removeAll() {
        repository.removeAll()
        _state.update { it.copy(accounts = emptyList(), codes = emptyMap(), secondsRemaining = emptyMap(), editMode = false) }
    }

    fun eraseAll() {
        repository.removeAll()
        settings.edit { remove("initialized") }
        _state.update {
            it.copy(
                accounts = emptyList(),
                codes = emptyMap(),
                secondsRemaining = emptyMap(),
                mode = AppMode.Welcome,
                welcomeSessionId = it.welcomeSessionId + 1,
                overlay = AccountsOverlay.None,
                editMode = false,
                searchQuery = ""
            )
        }
    }

    fun addMultiple(tokens: List<Token>) {
        var current = _state.value.accounts
        tokens.forEach { token -> current = repository.add(token, current) }
        _state.update { it.copy(accounts = current) }
        updateCodes()
    }

    fun completeWelcome() {
        settings.edit { putBoolean("initialized", true) }
        _state.update { it.copy(mode = AppMode.Accounts) }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    // MARK: - Private

    private fun updateCodes() {
        val now = System.currentTimeMillis()
        val accounts = _state.value.accounts
        val codes = accounts.associate { token ->
            token.id to TotpCodeGenerator.generateCode(token.generator, now)
        }
        val remaining = accounts.associate { token ->
            val period = (token.generator.factor as? OtpFactor.Timer)?.period ?: 30
            token.id to TotpCodeGenerator.secondsRemaining(period, now)
        }
        _state.update { it.copy(codes = codes, secondsRemaining = remaining) }
    }

    private fun tickerFlow() = flow {
        while (true) {
            val now = System.currentTimeMillis()
            delay(1_000L - (now % 1_000L))
            emit(Unit)
        }
    }
}
