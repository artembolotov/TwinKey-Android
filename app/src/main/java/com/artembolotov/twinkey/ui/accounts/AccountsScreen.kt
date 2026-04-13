package com.artembolotov.twinkey.ui.accounts

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.GoogleAuthMigrationParser
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.domain.TokenUrlParser
import com.artembolotov.twinkey.ui.add.AccountAddedScreen
import com.artembolotov.twinkey.ui.add.AddManuallyScreen
import com.artembolotov.twinkey.ui.add.QrScannerScreen
import com.artembolotov.twinkey.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

sealed class AddFlow {
    object None : AddFlow()
    object Scanner : AddFlow()
    object Manual : AddFlow()
    data class Added(val token: Token) : AddFlow()
}

/**
 * Порт AccountsScreen.swift.
 *
 * Поиск фильтрует список по issuer/name.
 * FAB → QR-сканер. Ячейки: свайп влево → удалить, ручка → перетащить.
 * Нажатие → редактировать (BottomSheet).
 * Settings → BottomSheet с настройками.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    vm: AccountsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.accounts_code_copied)
    val invalidQrMessage = stringResource(R.string.scan_invalid_qr)
    val scope = rememberCoroutineScope()

    var addFlow by remember { mutableStateOf<AddFlow>(AddFlow.None) }
    var editingToken by remember { mutableStateOf<Token?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }

    val accountsEmpty by remember { derivedStateOf { state.accounts.isEmpty() } }
    LaunchedEffect(accountsEmpty) {
        if (accountsEmpty) editMode = false
    }

    val filteredAccounts by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) state.accounts
            else state.accounts.filter {
                it.issuer.contains(searchQuery, ignoreCase = true) ||
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val manualSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Полноэкранный QR-сканер
    if (addFlow is AddFlow.Scanner) {
        QrScannerScreen(
            onScanned = { url ->
                when {
                    GoogleAuthMigrationParser.isMigrationUrl(url) -> {
                        val (tokens, skipped) = runCatching {
                            GoogleAuthMigrationParser.parse(url)
                        }.getOrElse { Pair(emptyList(), emptyList()) }

                        addFlow = AddFlow.None
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
                            addFlow = AddFlow.Added(token)
                        } else {
                            addFlow = AddFlow.None
                            vm.showMessage(invalidQrMessage)
                        }
                    }
                }
            },
            onAddManually = { addFlow = AddFlow.Manual },
            onCancel = { addFlow = AddFlow.None }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TwinKey") },
                actions = {
                    if (editMode) {
                        TextButton(onClick = { editMode = false }) {
                            Text(
                                stringResource(R.string.accounts_done),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!editMode) {
                FloatingActionButton(onClick = { addFlow = AddFlow.Scanner }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add_account))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.accounts.isEmpty()) {
            AccountsEmptyView(
                onAddAccount = { addFlow = AddFlow.Scanner },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Поиск
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.accounts_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.accounts_search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AccountsListView(
                    accounts = filteredAccounts,
                    codes = state.codes,
                    secondsRemaining = state.secondsRemaining,
                    onCopyCode = { code ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", code)))
                        }
                        vm.showMessage(copiedMessage)
                    },
                    onEditAccount = { id ->
                        editingToken = state.accounts.find { it.id == id }
                    },
                    onDeleteAccount = { id -> vm.deleteAccount(id) },
                    onMove = { from, to -> vm.moveAccount(from, to) },
                    isDraggable = editMode && searchQuery.isBlank(),
                    isEditMode = editMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // BottomSheet: ручной ввод
    if (addFlow is AddFlow.Manual) {
        ModalBottomSheet(
            onDismissRequest = { addFlow = AddFlow.None },
            sheetState = manualSheetState,
            modifier = Modifier.fillMaxWidth()
        ) {
            AddManuallyScreen(
                onDone = { token ->
                    scope.launch {
                        manualSheetState.hide()
                        vm.addAccount(token)
                        addFlow = AddFlow.Added(token)
                    }
                },
                onCancel = {
                    scope.launch {
                        manualSheetState.hide()
                        addFlow = AddFlow.None
                    }
                }
            )
        }
    }

    // BottomSheet: аккаунт добавлен
    if (addFlow is AddFlow.Added) {
        val token = (addFlow as AddFlow.Added).token
        ModalBottomSheet(
            onDismissRequest = { addFlow = AddFlow.None },
            sheetState = addedSheetState
        ) {
            AccountAddedScreen(
                token = token,
                code = state.codes[token.id] ?: "",
                secondsRemaining = state.secondsRemaining[token.id] ?: 30,
                onDone = {
                    scope.launch {
                        addedSheetState.hide()
                        addFlow = AddFlow.None
                    }
                },
                onCopied = { vm.showMessage(copiedMessage) }
            )
        }
    }

    // BottomSheet: редактирование
    editingToken?.let { token ->
        ModalBottomSheet(
            onDismissRequest = { editingToken = null },
            sheetState = editSheetState
        ) {
            AccountEditScreen(
                token = token,
                onDone = { updated ->
                    scope.launch {
                        editSheetState.hide()
                        vm.updateAccount(updated)
                        editingToken = null
                    }
                },
                onDelete = { id ->
                    scope.launch {
                        editSheetState.hide()
                        vm.deleteAccount(id)
                        editingToken = null
                    }
                },
                onCancel = {
                    scope.launch {
                        editSheetState.hide()
                        editingToken = null
                    }
                }
            )
        }
    }

    // BottomSheet: настройки
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = settingsSheetState
        ) {
            SettingsScreen(
                accounts = state.accounts,
                onImportAccounts = { tokens -> vm.addMultiple(tokens) },
                onDeleteAll = { vm.removeAll() },
                onMessage = { msg -> vm.showMessage(msg) },
                onDismiss = {
                    scope.launch {
                        settingsSheetState.hide()
                        showSettings = false
                    }
                },
                onEditAccounts = {
                    scope.launch {
                        settingsSheetState.hide()
                        showSettings = false
                        editMode = true
                    }
                }
            )
        }
    }
}
