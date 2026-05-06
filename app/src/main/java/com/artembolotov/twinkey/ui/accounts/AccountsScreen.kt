package com.artembolotov.twinkey.ui.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.GoogleAuthMigrationParser
import com.artembolotov.twinkey.domain.TokenUrlParser
import com.artembolotov.twinkey.ui.add.QrScannerScreen
import com.artembolotov.twinkey.ui.theme.PageBackgroundDark
import com.artembolotov.twinkey.ui.theme.PageBackgroundLight
import kotlinx.coroutines.flow.drop

/**
 * Порт AccountsScreen.swift.
 *
 * Весь UI-стейт (активный оверлей, editMode, searchQuery) живёт во ViewModel.
 * searchActive — локальный UI-стейт фокуса, не переживает config change (intentionally).
 * ModalBottomSheetState остаётся в composable — это чисто анимационный UI-concern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    vm: AccountsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottomPx by rememberUpdatedState(WindowInsets.ime.getBottom(density))

    LaunchedEffect(density) {
        snapshotFlow { imeBottomPx }
            .drop(1)
            .collect { bottom ->
                if (bottom == 0) focusManager.clearFocus()
            }
    }

    var searchActive by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val copiedMessage = stringResource(R.string.accounts_code_copied)
    val invalidQrMessage = stringResource(R.string.scan_invalid_qr)
    val importSuccess = stringResource(R.string.backup_import_success)

    val sheetStates = rememberAccountsSheetStates()

    // Фильтрация пересчитывается только при изменении списка или запроса,
    // а не при каждом тике таймера (codes обновляются каждую секунду)
    val filteredAccounts = remember(state.accounts, state.searchQuery) {
        if (state.searchQuery.isBlank()) state.accounts
        else state.accounts.filter {
            it.issuer.contains(state.searchQuery, ignoreCase = true) ||
            it.name.contains(state.searchQuery, ignoreCase = true)
        }
    }

    BackHandler(enabled = state.searchQuery.isNotEmpty() || state.editMode) {
        if (state.searchQuery.isNotEmpty()) vm.setSearchQuery("")
        else vm.setEditMode(false)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // Полноэкранный QR-сканер
    if (state.overlay is AccountsOverlay.Scanner) {
        QrScannerScreen(
            onScanned = { url ->
                when {
                    GoogleAuthMigrationParser.isMigrationUrl(url) -> {
                        val (tokens, skipped) = runCatching {
                            GoogleAuthMigrationParser.parse(url)
                        }.getOrElse { Pair(emptyList(), emptyList()) }

                        vm.dismissOverlay()
                        if (tokens.isNotEmpty()) {
                            vm.addMultiple(tokens)
                            val msg = if (skipped.isEmpty())
                                "${tokens.size} account(s) imported from Google Authenticator"
                            else
                                "${tokens.size} imported, ${skipped.size} skipped (HOTP)"
                            vm.showMessage(msg)
                        } else {
                            vm.showMessage(invalidQrMessage)
                        }
                    }
                    else -> {
                        val token = runCatching { TokenUrlParser.parse(url) }.getOrNull()
                        if (token != null) {
                            vm.addAccount(token)
                            vm.showOverlay(AccountsOverlay.Added(token))
                        } else {
                            vm.dismissOverlay()
                            vm.showMessage(invalidQrMessage)
                        }
                    }
                }
            },
            onAddManually = { vm.showOverlay(AccountsOverlay.Manual) },
            onCancel = { vm.dismissOverlay() }
        )
        return
    }

    val pageBackground = if (isSystemInDarkTheme()) PageBackgroundDark else PageBackgroundLight

    Scaffold(
        containerColor = pageBackground,
        topBar = {},
        floatingActionButton = {
            AnimatedVisibility(
                visible = !state.editMode && !searchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(onClick = { vm.showOverlay(AccountsOverlay.Scanner) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add_account))
                }
            }
        },
    ) { padding ->
        if (state.accounts.isEmpty()) {
            AccountsEmptyView(
                onRestoreFromBackup = { vm.showOverlay(AccountsOverlay.ImportFromEmpty) },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (!searchActive) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
            ) {
                AccountsTopBar(
                    visible = !searchActive && !isLandscape,
                    editMode = state.editMode,
                    pageBackground = pageBackground,
                    scrollBehavior = scrollBehavior,
                    onDoneClick = { vm.setEditMode(false) },
                    onSettingsClick = { vm.showOverlay(AccountsOverlay.Settings) }
                )

                AccountsSearchBar(
                    query = state.searchQuery,
                    searchActive = searchActive,
                    editMode = state.editMode,
                    isLandscape = isLandscape,
                    onQueryChange = { vm.setSearchQuery(it) },
                    onSearchActiveChange = { searchActive = it },
                    onClearQuery = { vm.setSearchQuery("") },
                    onDoneClick = { vm.setEditMode(false) },
                    onSettingsClick = { vm.showOverlay(AccountsOverlay.Settings) }
                )

                AccountsListView(
                    accounts = filteredAccounts,
                    codes = state.codes,
                    secondsRemaining = state.secondsRemaining,
                    onCopyCode = { code ->
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("", code))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            vm.showMessage(copiedMessage)
                        }
                    },
                    onEditAccount = { id ->
                        val token = state.accounts.find { it.id == id } ?: return@AccountsListView
                        vm.showOverlay(AccountsOverlay.Editing(token))
                    },
                    onMove = { from, to -> vm.moveAccount(from, to) },
                    isDraggable = state.editMode && state.searchQuery.isBlank(),
                    isEditMode = state.editMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    focusManager.clearFocus()
                                }
                            }
                        }
                )
            }
        }
    }

    AccountsSheets(
        state = state,
        vm = vm,
        sheetStates = sheetStates,
        copiedMessage = copiedMessage,
        importSuccess = importSuccess,
    )
}
