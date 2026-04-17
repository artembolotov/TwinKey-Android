package com.artembolotov.twinkey.ui.accounts

import android.app.Application
import android.content.Context
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

// Порт AppState + TimerService из iOS
class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val mode: AppMode = AppMode.Unknown,
        val accounts: List<Token> = emptyList(),
        val codes: Map<String, String> = emptyMap(),          // id → текущий код
        val secondsRemaining: Map<String, Int> = emptyMap(),  // id → секунды до смены
        val message: String? = null,
        val welcomeSessionId: Int = 0
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

        // Порт TimerService: тик каждую секунду
        viewModelScope.launch {
            tickerFlow().collect { updateCodes() }
        }
    }

    // MARK: - Actions (порт AccountAction)

    fun addAccount(token: Token) {
        val updated = repository.add(token, _state.value.accounts)
        _state.update { it.copy(accounts = updated, mode = AppMode.Accounts) }
        updateCodes()
    }

    fun deleteAccount(id: String) {
        val updated = repository.delete(id, _state.value.accounts)
        _state.update { it.copy(accounts = updated) }
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
        _state.update { it.copy(accounts = emptyList(), codes = emptyMap(), secondsRemaining = emptyMap()) }
    }

    fun eraseAll() {
        repository.removeAll()
        settings.edit().remove("initialized").apply()
        _state.update {
            it.copy(
                accounts = emptyList(),
                codes = emptyMap(),
                secondsRemaining = emptyMap(),
                mode = AppMode.Welcome,
                welcomeSessionId = it.welcomeSessionId + 1
            )
        }
    }

    fun addMultiple(tokens: List<Token>) {
        var current = _state.value.accounts
        tokens.forEach { token -> current = repository.add(token, current) }
        _state.update { it.copy(accounts = current) }
        updateCodes()
    }

    // Порт CoreAction.resetSettings — завершение онбординга
    fun completeWelcome() {
        settings.edit().putBoolean("initialized", true).apply()
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

    // Порт TimerService: паблишер раз в секунду
    private fun tickerFlow() = flow {
        while (true) {
            delay(1_000L)
            emit(Unit)
        }
    }
}
