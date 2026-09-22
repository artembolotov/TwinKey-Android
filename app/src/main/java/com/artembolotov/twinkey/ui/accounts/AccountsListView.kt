package com.artembolotov.twinkey.ui.accounts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import com.artembolotov.twinkey.domain.Token
import com.artembolotov.twinkey.ui.theme.CardBackgroundDark
import com.artembolotov.twinkey.ui.theme.CardBackgroundLight
import com.artembolotov.twinkey.ui.components.OtpCodeView
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

// A cell is never narrower than this, so the column count follows the window width: one column on a
// phone, the cover screen of a foldable or a split-screen half, two on an unfolded foldable or a
// phone in landscape, more on a tablet.
private val MinCellWidth = 300.dp
private val CardSpacing = 12.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsListView(
    accounts: List<Token>,
    codes: Map<String, String>,
    secondsRemaining: Map<String, Int>,
    onCopyCode: (String) -> Unit,
    onEditAccount: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    isDraggable: Boolean = true,
    isEditMode: Boolean = false
) {
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
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
                return if (!gridState.canScrollForward && !gridState.canScrollBackward) {
                    overscrollEffect?.applyToScroll(available, source) { Offset.Zero } ?: Offset.Zero
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (!gridState.canScrollForward && !gridState.canScrollBackward) {
                    overscrollEffect?.applyToFling(available) { Velocity.Zero }
                    available
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val safeHorizontal = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val startPadding = contentPadding.calculateStartPadding(layoutDirection) +
        safeHorizontal.calculateStartPadding(layoutDirection) + 16.dp
    val endPadding = contentPadding.calculateEndPadding(layoutDirection) +
        safeHorizontal.calculateEndPadding(layoutDirection) + 16.dp

    val cardBackground = if (isSystemInDarkTheme()) CardBackgroundDark else CardBackgroundLight

    BoxWithConstraints(
        modifier = modifier
            .nestedScroll(nestedScrollConnection)
            .then(if (overscrollEffect != null) Modifier.overscroll(overscrollEffect) else Modifier)
    ) {
        val available = maxWidth - startPadding - endPadding
        val columns = ((available + CardSpacing) / (MinCellWidth + CardSpacing)).toInt().coerceAtLeast(1)
        // A single column keeps the iOS grouped list: one rounded block split by dividers. Grouping
        // cannot span columns, so with several columns every account is a card of its own.
        val grouped = columns == 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = startPadding,
                top = contentPadding.calculateTopPadding(),
                end = endPadding,
                bottom = contentPadding.calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            verticalArrangement = if (grouped) Arrangement.Top else Arrangement.spacedBy(CardSpacing),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(accounts, key = { _, token -> token.id }) { index, token ->
                val shape: Shape = when {
                    !grouped || accounts.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    index == accounts.size - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RectangleShape
                }

                ReorderableItem(reorderState, key = token.id) { isDragging ->
                    Column(
                        modifier = Modifier
                            .background(
                                if (isDragging) MaterialTheme.colorScheme.surfaceVariant else cardBackground,
                                shape
                            )
                            .clip(shape)
                    ) {
                        if (grouped && index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                        AccountCell(
                            token = token,
                            code = codes[token.id] ?: "",
                            secondsRemaining = secondsRemaining[token.id] ?: 30,
                            onCopyCode = onCopyCode,
                            onEdit = { onEditAccount(token.id) },
                            isEditMode = isEditMode,
                            // Cards in a row should share a height, so a long title is cut, not wrapped.
                            singleLineTitle = !grouped,
                            dragHandle = if (isDraggable) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.accounts_reorder_hint),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .draggableHandle()
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
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
    singleLineTitle: Boolean = false,
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
            val nameColor = MaterialTheme.colorScheme.onSurfaceVariant
            // One Text rather than a Row of two: in a Row a long issuer squeezes the name into a
            // sliver that wraps a few letters per line on a narrow screen.
            val title = remember(token.issuer, token.name, nameColor) {
                buildAnnotatedString {
                    if (token.issuer.isNotEmpty()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.issuer) }
                        if (token.name.isNotEmpty()) {
                            withStyle(SpanStyle(color = nameColor)) { append("  ${token.name}") }
                        }
                    } else {
                        append(token.name)
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (singleLineTitle) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
            if (!isEditMode) {
                Spacer(Modifier.height(4.dp))
                OtpCodeView(
                    code = code,
                    secondsRemaining = secondsRemaining,
                    fullWidth = true,
                    onTap = onCopyCode
                )
            }
        }

        // Ручка перетаскивания
        dragHandle?.invoke()
    }
}
