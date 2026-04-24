package com.artembolotov.twinkey.ui.accounts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.components.OtpCodeView
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Порт AccountsListView.swift + AccountCell.
 * Свайп влево → удалить (только в edit mode). Долгое нажатие на ручку → перетаскивать (только в edit mode).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsListView(
    accounts: List<Token>,
    codes: Map<String, String>,
    secondsRemaining: Map<String, Int>,
    onCopyCode: (String) -> Unit,
    onEditAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isDraggable: Boolean = true,
    isEditMode: Boolean = false
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    val overscrollEffect = rememberOverscrollEffect()
    val nestedScrollConnection = remember(overscrollEffect) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (!lazyListState.canScrollForward && !lazyListState.canScrollBackward) {
                    overscrollEffect?.applyToScroll(available, source) { Offset.Zero } ?: Offset.Zero
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (!lazyListState.canScrollForward && !lazyListState.canScrollBackward) {
                    overscrollEffect?.applyToFling(available) { Velocity.Zero }
                    available
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    Box(
        modifier = modifier
            .nestedScroll(nestedScrollConnection)
            .then(if (overscrollEffect != null) Modifier.overscroll(overscrollEffect) else Modifier)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(accounts, key = { _, token -> token.id }) { index, token ->
                val shape: Shape = when {
                    accounts.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    index == accounts.size - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RectangleShape
                }

                ReorderableItem(reorderState, key = token.id) { isDragging ->
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .background(
                                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface,
                                shape
                            )
                            .clip(shape)
                    ) {
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
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
                                                .draggableHandle()
                                        )
                                    }
                                } else null
                            )
                        }
                        if (isEditMode) {
                            SwipeToDeleteCell(onDelete = { onDeleteAccount(token.id) }) { cell() }
                        } else {
                            cell()
                        }
                    }
                }
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
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

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
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
