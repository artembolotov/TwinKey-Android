package com.artembolotov.twinkey.ui.accounts

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.artembolotov.twinkey.ui.add.AccountAddedScreen
import com.artembolotov.twinkey.ui.add.AddManuallyScreen
import com.artembolotov.twinkey.ui.components.AppModalBottomSheet
import com.artembolotov.twinkey.ui.components.AppSheetState
import com.artembolotov.twinkey.ui.components.rememberAppSheetState
import com.artembolotov.twinkey.ui.settings.AccountsImportScreen
import com.artembolotov.twinkey.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class AccountsSheetStates internal constructor(
    val manual: AppSheetState,
    val added: AppSheetState,
    val edit: AppSheetState,
    val settings: AppSheetState,
    val importFromEmpty: AppSheetState,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAccountsSheetStates(): AccountsSheetStates = AccountsSheetStates(
    manual = rememberAppSheetState(),
    added = rememberAppSheetState(),
    edit = rememberAppSheetState(),
    settings = rememberAppSheetState(),
    importFromEmpty = rememberAppSheetState(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheets(
    state: AccountsViewModel.UiState,
    vm: AccountsViewModel,
    sheetStates: AccountsSheetStates,
    copiedMessage: String,
    importSuccess: String,
) {
    val scope = rememberCoroutineScope()

    when (val overlay = state.overlay) {
        AccountsOverlay.Manual -> {
            AppModalBottomSheet(
                appSheetState = sheetStates.manual,
                onDismissRequest = { vm.dismissOverlay() },
                modifier = Modifier.fillMaxWidth()
            ) {
                AddManuallyScreen(
                    onDone = { token ->
                        scope.launch {
                            sheetStates.manual.hide()
                            vm.addAccount(token)
                            vm.showOverlay(AccountsOverlay.Added(token))
                        }
                    },
                    onCancel = {
                        scope.launch {
                            sheetStates.manual.hide()
                            vm.dismissOverlay()
                        }
                    }
                )
            }
        }

        is AccountsOverlay.Added -> {
            val token = overlay.token
            AppModalBottomSheet(
                appSheetState = sheetStates.added,
                onDismissRequest = { vm.dismissOverlay() }
            ) {
                AccountAddedScreen(
                    token = token,
                    code = state.codes[token.id] ?: "",
                    secondsRemaining = state.secondsRemaining[token.id] ?: 30,
                    onDone = {
                        scope.launch {
                            sheetStates.added.hide()
                            vm.dismissOverlay()
                        }
                    },
                    onCopied = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            vm.showMessage(copiedMessage)
                        }
                    }
                )
            }
        }

        is AccountsOverlay.Editing -> {
            val token = overlay.token
            AppModalBottomSheet(
                appSheetState = sheetStates.edit,
                onDismissRequest = { vm.dismissOverlay() }
            ) {
                AccountEditScreen(
                    token = token,
                    onDone = { updated ->
                        scope.launch {
                            sheetStates.edit.hide()
                            vm.updateAccount(updated)
                            vm.dismissOverlay()
                        }
                    },
                    onDelete = { id ->
                        scope.launch {
                            sheetStates.edit.hide()
                            vm.deleteAccount(id)
                            vm.dismissOverlay()
                        }
                    },
                    onCancel = {
                        scope.launch {
                            sheetStates.edit.hide()
                            vm.dismissOverlay()
                        }
                    }
                )
            }
        }

        AccountsOverlay.ImportFromEmpty -> {
            AppModalBottomSheet(
                appSheetState = sheetStates.importFromEmpty,
                onDismissRequest = { vm.dismissOverlay() }
            ) {
                AccountsImportScreen(
                    onImport = { tokens ->
                        scope.launch {
                            sheetStates.importFromEmpty.hide()
                            vm.addMultiple(tokens)
                            vm.dismissOverlay()
                            vm.showMessage(importSuccess)
                        }
                    },
                    onError = { msg ->
                        scope.launch {
                            sheetStates.importFromEmpty.hide()
                            vm.dismissOverlay()
                            vm.showMessage(msg)
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            sheetStates.importFromEmpty.hide()
                            vm.dismissOverlay()
                        }
                    }
                )
            }
        }

        AccountsOverlay.Settings -> {
            AppModalBottomSheet(
                appSheetState = sheetStates.settings,
                onDismissRequest = { vm.dismissOverlay() }
            ) {
                SettingsScreen(
                    accounts = state.accounts,
                    onImportAccounts = { tokens -> vm.addMultiple(tokens) },
                    onDeleteAll = { vm.removeAll() },
                    onEraseAll = {
                        scope.launch {
                            sheetStates.settings.hide()
                            vm.eraseAll()
                        }
                    },
                    onMessage = { msg -> vm.showMessage(msg) },
                    onDismiss = {
                        scope.launch {
                            sheetStates.settings.hide()
                            vm.dismissOverlay()
                        }
                    },
                    onEditAccounts = {
                        scope.launch {
                            sheetStates.settings.hide()
                            vm.dismissOverlay()
                            vm.setEditMode(true)
                        }
                    }
                )
            }
        }

        AccountsOverlay.None,
        AccountsOverlay.Scanner -> Unit
    }
}
