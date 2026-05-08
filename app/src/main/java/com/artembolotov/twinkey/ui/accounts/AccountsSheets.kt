package com.artembolotov.twinkey.ui.accounts

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.artembolotov.twinkey.data.ImportResult
import com.artembolotov.twinkey.ui.add.AccountAddedScreen
import com.artembolotov.twinkey.ui.components.AppModalBottomSheet
import com.artembolotov.twinkey.ui.components.AppSheetState
import com.artembolotov.twinkey.ui.components.rememberAppSheetState
import com.artembolotov.twinkey.ui.settings.AccountsImportPickerSheet
import com.artembolotov.twinkey.ui.settings.AccountsImportSelectionScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class AccountsSheetStates internal constructor(
    val added: AppSheetState,
    val importFromEmpty: AppSheetState,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAccountsSheetStates(): AccountsSheetStates = AccountsSheetStates(
    added = rememberAppSheetState(),
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

        AccountsOverlay.ImportFromEmpty -> {
            var importResult by remember { mutableStateOf<ImportResult?>(null) }

            AppModalBottomSheet(
                appSheetState = sheetStates.importFromEmpty,
                onDismissRequest = { vm.dismissOverlay() }
            ) {
                AccountsImportPickerSheet(
                    onFileParsed = { result ->
                        scope.launch {
                            sheetStates.importFromEmpty.hide()
                            importResult = result
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

            importResult?.let { result ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AccountsImportSelectionScreen(
                        importResult = result,
                        onImport = { tokens ->
                            vm.addMultiple(tokens)
                            vm.dismissOverlay()
                            vm.showMessage(importSuccess)
                        },
                        onDismiss = { vm.dismissOverlay() }
                    )
                }
            }
        }

        AccountsOverlay.None,
        AccountsOverlay.Settings,
        AccountsOverlay.Manual,
        is AccountsOverlay.Editing,
        AccountsOverlay.Scanner -> Unit
    }
}
