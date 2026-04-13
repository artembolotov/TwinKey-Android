package com.artembolotov.twinkey.ui.accounts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.OtpCodeView
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Порт AccountsListView.swift + AccountCell.
 * Свайп влево → удалить (только в edit mode). Долгое нажатие на ручку → перетаскивать (только в edit mode).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsListView(
    accounts: List<Token>,
    codes: Map<String, String>,
    secondsRemaining: Map<String, Int>,
    onCopyCode: (String) -> Unit,
    onEditAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    isDraggable: Boolean = true,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
    ) {
        items(accounts, key = { it.id }) { token ->
            ReorderableItem(reorderState, key = token.id) { isDragging ->
                val cell: @Composable () -> Unit = {
                    AccountCell(
                        token = token,
                        code = codes[token.id] ?: "",
                        secondsRemaining = secondsRemaining[token.id] ?: 30,
                        onCopyCode = onCopyCode,
                        onEdit = { onEditAccount(token.id) },
                        isEditMode = isEditMode,
                        dragHandle = if (isDraggable) {
                            {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .longPressDraggableHandle()
                                )
                            }
                        } else null,
                        isDragging = isDragging
                    )
                }
                if (isEditMode) {
                    SwipeToDeleteCell(onDelete = { onDeleteAccount(token.id) }) { cell() }
                } else {
                    cell()
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCell(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else Color.Transparent,
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        content()
    }
}

/**
 * Порт AccountCell из AccountsListView.swift.
 * В edit mode: скрыт OTP-код, тап → редактировать.
 * В обычном режиме: показывается OTP-код, тап — ничего (копирование внутри OtpCodeView).
 * Ручка перетаскивания справа.
 */
@Composable
fun AccountCell(
    token: Token,
    code: String,
    secondsRemaining: Int,
    onCopyCode: (String) -> Unit,
    onEdit: () -> Unit,
    isEditMode: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .clickable(enabled = isEditMode) { onEdit() }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                if (token.issuer.isNotEmpty()) {
                    Text(
                        text = token.issuer,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (token.name.isNotEmpty()) {
                        Text(
                            text = "  ${token.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(text = token.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!isEditMode) {
                Spacer(Modifier.height(4.dp))
                OtpCodeView(
                    code = code,
                    secondsRemaining = secondsRemaining,
                    onTap = onCopyCode
                )
            }
        }

        // Ручка перетаскивания
        dragHandle?.invoke()
    }
}
