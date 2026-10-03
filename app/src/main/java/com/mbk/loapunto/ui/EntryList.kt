package com.mbk.loapunto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.Learned
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A block of cards under an optional heading; only a hand-ordered block can be dragged. */
internal data class Section(val title: String?, val entries: List<Entry>, val reorderable: Boolean = false)

/** A heading above a block of cards: Later's two halves, visible at a glance. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/**
 * Cards with long-press for the move sheet. The hand-ordered section also gets a drag grip; the new
 * order is shown locally while dragging and saved when the drag ends.
 */
@Composable
internal fun EntryList(
    sections: List<Section>,
    showStatus: Boolean,
    onOpen: (Entry) -> Unit,
    onMove: (Entry, Status?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Each hint is shown until the gesture has been used once, then never again.
    var teachHold by remember { mutableStateOf(!Learned.has(context, Learned.HOLD)) }
    var teachDrag by remember { mutableStateOf(!Learned.has(context, Learned.DRAG)) }
    var sheetFor by remember { mutableStateOf<Entry?>(null) }
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // At most one section is hand-ordered, and dragging only happens inside it.
    val handIds = sections.firstOrNull { it.reorderable }?.entries?.map { it.id }
    val shown = remember(sections, localOrder) {
        val order = localOrder
        if (order == null || order.toSet() != handIds?.toSet()) sections
        else sections.map { section ->
            if (!section.reorderable) section
            else section.copy(entries = section.entries.associateBy { it.id }.let { byId -> order.map { byId.getValue(it) } })
        }
    }
    val entries = shown.flatMap { it.entries }
    val currentHand by rememberUpdatedState(shown.firstOrNull { it.reorderable }?.entries?.map { it.id }.orEmpty())
    // Drop the local order once the store has caught up with it.
    LaunchedEffect(sections, dragging) {
        if (!dragging && localOrder == handIds) localOrder = null
    }

    val listState = rememberLazyListState()
    // When one entry joins the bottom (a new capture), scroll down so you see it land.
    var knownIds by remember { mutableStateOf(entries.map { it.id }.toSet()) }
    LaunchedEffect(entries) {
        val ids = entries.map { it.id }
        val added = ids.filterNot { it in knownIds }
        knownIds = ids.toSet()
        if (added.size == 1 && ids.last() == added[0]) {
            listState.animateScrollToItem(ids.lastIndex + shown.count { it.title != null })
        }
    }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val ids = currentHand.toMutableList()
        val fromIndex = ids.indexOf(from.key)
        val toIndex = ids.indexOf(to.key)
        // Headings and dated cards are not part of the hand-ordered block.
        if (fromIndex >= 0 && toIndex >= 0) {
            ids.add(toIndex, ids.removeAt(fromIndex))
            localOrder = ids
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        shown.forEach { section ->
            section.title?.let { title -> item(key = "section-$title") { SectionHeader(title) } }
            itemsIndexed(section.entries, key = { _, entry -> entry.id }) { index, entry ->
                ReorderableItem(reorderState, key = entry.id, enabled = section.reorderable) { isDragging ->
                    EntryCard(
                        entry = entry,
                        emphasis = if (section.reorderable) emphasisFor(index, section.entries.size) else null,
                        showStatus = showStatus,
                        onClick = { onOpen(entry) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sheetFor = entry
                            if (teachHold) {
                                Learned.mark(context, Learned.HOLD)
                                teachHold = false
                            }
                        },
                        elevation = if (isDragging) 8f else 1f,
                        handle = if (!section.reorderable) null else {
                            {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            dragging = true
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            dragging = false
                                            localOrder?.let(EntryStore::reorder)
                                            if (teachDrag) {
                                                Learned.mark(context, Learned.DRAG)
                                                teachDrag = false
                                            }
                                        },
                                    ),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_drag),
                                        contentDescription = stringResource(R.string.drag_to_reorder),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        val canDrag = handIds != null
        val hint = when {
            teachHold && teachDrag && canDrag -> R.string.hint_reorder
            teachHold -> R.string.hint_hold
            teachDrag && canDrag -> R.string.hint_drag
            else -> null
        }
        hint?.let { text ->
            item(key = "hint") {
                Text(
                    stringResource(text),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    sheetFor?.let { entry ->
        MoveSheet(
            entry = entry,
            onDismiss = { sheetFor = null },
            onMove = { target ->
                sheetFor = null
                onMove(entry, target)
            },
            onEdit = {
                sheetFor = null
                onOpen(entry)
            },
            onParkUntil = { day ->
                sheetFor = null
                EntryStore.parkUntil(entry.id, day)
            },
        )
    }
}

@Composable
internal fun EmptyState(tab: Status?, searchActive: Boolean) {
    val (title, hint) = when {
        searchActive -> R.string.empty_search to R.string.empty_search_hint
        tab == null -> R.string.empty_searching to R.string.empty_searching_hint
        tab == Status.INBOX -> R.string.empty_inbox to R.string.empty_inbox_hint
        tab == Status.TODAY -> R.string.empty_today to R.string.empty_today_hint
        tab == Status.LATER -> R.string.empty_later to R.string.empty_later_hint
        tab == Status.DONE -> R.string.empty_done to R.string.empty_done_hint
        else -> R.string.empty_trash to R.string.empty_trash_hint
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(64.dp))
    }
}
