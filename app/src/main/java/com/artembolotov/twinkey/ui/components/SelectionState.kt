package com.artembolotov.twinkey.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap

// Checkbox selection keyed by account id that survives activity recreation — folding or unfolding
// a foldable recreates the activity, and a plain remember would drop the user's picks.
@Composable
fun rememberSelection(initiallySelected: () -> Collection<String> = { emptyList() }): SnapshotStateMap<String, Boolean> =
    rememberSaveable(saver = SelectionSaver) { selectionOf(initiallySelected()) }

// Only selected ids are saved: an id missing from the map reads as unselected.
private val SelectionSaver = listSaver<SnapshotStateMap<String, Boolean>, String>(
    save = { map -> map.filterValues { it }.keys.toList() },
    restore = { ids -> selectionOf(ids) }
)

private fun selectionOf(ids: Collection<String>) =
    mutableStateMapOf<String, Boolean>().apply { ids.forEach { put(it, true) } }
