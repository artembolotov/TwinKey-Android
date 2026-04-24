package com.artembolotov.twinkey.ui.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.ui.theme.PageBackgroundDark
import com.artembolotov.twinkey.ui.theme.PageBackgroundLight
import com.artembolotov.twinkey.domain.GoogleAuthMigrationParser
import com.artembolotov.twinkey.domain.TokenUrlParser
import com.artembolotov.twinkey.ui.add.AccountAddedScreen
import com.artembolotov.twinkey.ui.add.AddManuallyScreen
import com.artembolotov.twinkey.ui.add.QrScannerScreen
import com.artembolotov.twinkey.ui.components.AppModalBottomSheet
import com.artembolotov.twinkey.ui.components.rememberAppSheetState
import com.artembolotov.twinkey.ui.settings.AccountsImportScreen
import com.artembolotov.twinkey.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val copiedMessage = stringResource(R.string.accounts_code_copied)
    val invalidQrMessage = stringResource(R.string.scan_invalid_qr)
    val importSuccess = stringResource(R.string.backup_import_success)

    // Анимационные состояния шторок — UI-concern, не переживают config change
    val manualSheetState = rememberAppSheetState()
    val addedSheetState = rememberAppSheetState()
    val editSheetState = rememberAppSheetState()
    val settingsSheetState = rememberAppSheetState()
    val importFromEmptySheetState = rememberAppSheetState()

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
                AnimatedVisibility(
                    visible = !searchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    MaterialTheme(
                        typography = MaterialTheme.typography.copy(
                            headlineSmall = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp
                            )
                        )
                    ) {
                        MediumTopAppBar(
                            title = { Text("TwinKey") },
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            scrollBehavior = scrollBehavior,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = pageBackground,
                                scrolledContainerColor = pageBackground
                            ),
                            actions = {
                                if (state.editMode) {
                                    TextButton(onClick = { vm.setEditMode(false) }) {
                                        Text(
                                            stringResource(R.string.accounts_done),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { vm.showOverlay(AccountsOverlay.Settings) }) {
                                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                                    }
                                }
                            }
                        )
                    }
                }

                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onFocusChanged { searchActive = it.isFocused },
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = state.searchQuery,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = remember { MutableInteractionSource() },
                            placeholder = { Text(stringResource(R.string.accounts_search)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.accounts_search_clear))
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        )
                    }
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
                    onDeleteAccount = { id -> vm.deleteAccount(id) },
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

    // BottomSheet: ручной ввод
    if (state.overlay is AccountsOverlay.Manual) {
        AppModalBottomSheet(
            appSheetState = manualSheetState,
            onDismissRequest = { vm.dismissOverlay() },
            modifier = Modifier.fillMaxWidth()
        ) {
            AddManuallyScreen(
                onDone = { token ->
                    scope.launch {
                        manualSheetState.hide()
                        vm.addAccount(token)
                        vm.showOverlay(AccountsOverlay.Added(token))
                    }
                },
                onCancel = {
                    scope.launch {
                        manualSheetState.hide()
                        vm.dismissOverlay()
                    }
                }
            )
        }
    }

    // BottomSheet: аккаунт добавлен
    if (state.overlay is AccountsOverlay.Added) {
        val token = (state.overlay as AccountsOverlay.Added).token
        AppModalBottomSheet(
            appSheetState = addedSheetState,
            onDismissRequest = { vm.dismissOverlay() }
        ) {
            AccountAddedScreen(
                token = token,
                code = state.codes[token.id] ?: "",
                secondsRemaining = state.secondsRemaining[token.id] ?: 30,
                onDone = {
                    scope.launch {
                        addedSheetState.hide()
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

    // BottomSheet: редактирование
    if (state.overlay is AccountsOverlay.Editing) {
        val token = (state.overlay as AccountsOverlay.Editing).token
        AppModalBottomSheet(
            appSheetState = editSheetState,
            onDismissRequest = { vm.dismissOverlay() }
        ) {
            AccountEditScreen(
                token = token,
                onDone = { updated ->
                    scope.launch {
                        editSheetState.hide()
                        vm.updateAccount(updated)
                        vm.dismissOverlay()
                    }
                },
                onDelete = { id ->
                    scope.launch {
                        editSheetState.hide()
                        vm.deleteAccount(id)
                        vm.dismissOverlay()
                    }
                },
                onCancel = {
                    scope.launch {
                        editSheetState.hide()
                        vm.dismissOverlay()
                    }
                }
            )
        }
    }

    // BottomSheet: импорт из пустого экрана
    if (state.overlay is AccountsOverlay.ImportFromEmpty) {
        AppModalBottomSheet(
            appSheetState = importFromEmptySheetState,
            onDismissRequest = { vm.dismissOverlay() }
        ) {
            AccountsImportScreen(
                onImport = { tokens ->
                    scope.launch {
                        importFromEmptySheetState.hide()
                        vm.addMultiple(tokens)
                        vm.dismissOverlay()
                        vm.showMessage(importSuccess)
                    }
                },
                onError = { msg ->
                    scope.launch {
                        importFromEmptySheetState.hide()
                        vm.dismissOverlay()
                        vm.showMessage(msg)
                    }
                },
                onDismiss = {
                    scope.launch {
                        importFromEmptySheetState.hide()
                        vm.dismissOverlay()
                    }
                }
            )
        }
    }

    // BottomSheet: настройки
    if (state.overlay is AccountsOverlay.Settings) {
        AppModalBottomSheet(
            appSheetState = settingsSheetState,
            onDismissRequest = { vm.dismissOverlay() }
        ) {
            SettingsScreen(
                accounts = state.accounts,
                onImportAccounts = { tokens -> vm.addMultiple(tokens) },
                onDeleteAll = { vm.removeAll() },
                onEraseAll = {
                    scope.launch {
                        settingsSheetState.hide()
                        vm.eraseAll()
                    }
                },
                onMessage = { msg -> vm.showMessage(msg) },
                onDismiss = {
                    scope.launch {
                        settingsSheetState.hide()
                        vm.dismissOverlay()
                    }
                },
                onEditAccounts = {
                    scope.launch {
                        settingsSheetState.hide()
                        vm.dismissOverlay()
                        vm.setEditMode(true)
                    }
                }
            )
        }
    }
}
