package com.artembolotov.twinkey.ui.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artembolotov.twinkey.core.AppMode
import com.artembolotov.twinkey.ui.accounts.AccountsScreen
import com.artembolotov.twinkey.ui.accounts.AccountsViewModel

/**
 * Порт WelcomeScreen.swift.
 *
 * Роутер верхнего уровня:
 *   Unknown  → ProgressView
 *   Welcome  → TutorialScreen (первый запуск)
 *   Accounts → AccountsScreen
 */
@Composable
fun WelcomeScreen(
    vm: AccountsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    when (state.mode) {
        AppMode.Unknown -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AppMode.Welcome -> {
            TutorialScreen(
                sessionId = state.welcomeSessionId,
                onGetStarted = { vm.completeWelcome() }
            )
        }
        AppMode.Accounts -> {
            AccountsScreen(vm = vm)
        }
    }
}
