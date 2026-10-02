package com.mbk.loapunto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface Screen {
    data object Lists : Screen
    data object Archive : Screen
    data object Sorting : Screen
    data class Detail(val id: String) : Screen
}

private val MainTabs = listOf(Status.INBOX, Status.TODAY, Status.LATER)

@Composable
fun LoApuntoApp(openRequest: String?, onOpenHandled: () -> Unit, onCapture: () -> Unit) {
    val entries by EntryStore.entries.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Status.INBOX) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var archive by rememberSaveable { mutableStateOf(false) }
    val open = openId?.let { id -> entries.firstOrNull { it.id == id } }

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            openId = openRequest
            onOpenHandled()
        }
    }
    LaunchedEffect(openId, open) { if (openId != null && open == null) openId = null }

    val screen = when {
        open != null -> Screen.Detail(open.id)
        sorting -> Screen.Sorting
        archive -> Screen.Archive
        else -> Screen.Lists
    }
    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
        when (target) {
            is Screen.Detail -> entries.firstOrNull { it.id == target.id }?.let { EntryDetailScreen(it, onBack = { openId = null }) }
            Screen.Sorting -> SortInboxScreen(entries, onExit = { sorting = false })
            Screen.Archive -> ArchiveScreen(entries, onOpen = { openId = it.id }, onBack = { archive = false })
            Screen.Lists -> ListScreen(
                entries,
                tab,
                onTab = { tab = it },
                onOpen = { openId = it.id },
                onSort = { sorting = true },
                onArchive = { archive = true },
                onCapture = onCapture,
            )
        }
    }
}

/** Moves (or, with a null target, deletes) an entry and offers Undo. */
@Composable
private fun rememberMover(snackbar: SnackbarHostState): (Entry, Status?) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(snackbar, scope) {
        { entry, target ->
            if (target == null) EntryStore.delete(entry.id) else EntryStore.move(entry.id, target)
            val message = when (target) {
                null -> "Deleted for good"
                Status.DONE -> "Marked done"
                Status.TRASH -> "Moved to trash"
                else -> "Moved to ${target.label}"
            }
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) EntryStore.upsert(entry)
            }
        }
    }
}

private fun ordered(entries: List<Entry>, status: Status): List<Entry> =
    entries.filter { it.status == status }.let { list ->
        if (status.isOrdered) list.sortedWith(compareBy<Entry> { it.rank }.thenByDescending { it.createdAt })
        else list.sortedByDescending { it.updatedAt }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    entries: List<Entry>,
    tab: Status,
    onTab: (Status) -> Unit,
    onOpen: (Entry) -> Unit,
    onSort: () -> Unit,
    onArchive: () -> Unit,
    onCapture: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val move = rememberMover(snackbar)
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    val searchActive = searching && query.isNotBlank()
    val visible = remember(entries, tab, query, searchActive) {
        if (searchActive) {
            val q = query.trim()
            entries.filter { it.status != Status.TRASH && (it.text.contains(q, true) || it.notes.contains(q, true)) }
                .sortedByDescending { it.updatedAt }
        } else ordered(entries, tab)
    }
    val inboxCount = entries.count { it.status == Status.INBOX }

    fun closeSearch() {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (searching) IconButton(onClick = ::closeSearch) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Close search")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search everything") },
                            singleLine = true,
                            colors = transparentFieldColors(),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                        LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    } else {
                        Text("LoApunto", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                        }
                        IconButton(onClick = onArchive) {
                            Icon(painterResource(R.drawable.ic_archive), contentDescription = "Done and trash")
                        }
                    } else if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("New") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!searching) {
                Tabs(MainTabs, entries, tab, onTab)
                if (tab == Status.INBOX && inboxCount >= 2) {
                    FilledTonalButton(
                        onClick = onSort,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text("Sort inbox one by one ($inboxCount)") }
                }
            }
            if (visible.isEmpty()) {
                EmptyState(if (searching) null else tab, searchActive)
            } else {
                EntryList(
                    entries = visible,
                    reorderable = !searchActive && tab.isOrdered,
                    showStatus = searchActive,
                    onOpen = onOpen,
                    onMove = move,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(entries: List<Entry>, onOpen: (Entry) -> Unit, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val move = rememberMover(snackbar)
    var tab by rememberSaveable { mutableStateOf(Status.DONE) }
    val visible = remember(entries, tab) { ordered(entries, tab) }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
                title = { Text("Done & Trash", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Tabs(listOf(Status.DONE, Status.TRASH), entries, tab, onSelect = { tab = it })
            if (visible.isEmpty()) EmptyState(tab, searchActive = false)
            else EntryList(visible, reorderable = false, showStatus = false, onOpen = onOpen, onMove = move)
        }
    }
}

@Composable
private fun Tabs(tabs: List<Status>, entries: List<Entry>, selected: Status, onSelect: (Status) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { status ->
            val count = entries.count { it.status == status }
            FilterChip(
                selected = status == selected,
                onClick = { onSelect(status) },
                label = {
                    Text(
                        if (count > 0) "${status.label}  $count" else status.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f).height(40.dp),
            )
        }
    }
}

/**
 * Cards with long-press for the move sheet. Reorderable lists also get a drag grip; the new order
 * is shown locally while dragging and saved when the drag ends.
 */
@Composable
private fun EntryList(
    entries: List<Entry>,
    reorderable: Boolean,
    showStatus: Boolean,
    onOpen: (Entry) -> Unit,
    onMove: (Entry, Status?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var sheetFor by remember { mutableStateOf<Entry?>(null) }
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val shown = remember(entries, localOrder) {
        val order = localOrder
        if (order == null || order.toSet() != entries.map { it.id }.toSet()) entries
        else entries.associateBy { it.id }.let { byId -> order.map { byId.getValue(it) } }
    }
    val currentShown by rememberUpdatedState(shown)
    // Drop the local order once the store has caught up with it.
    LaunchedEffect(entries, dragging) {
        if (!dragging && localOrder == entries.map { it.id }) localOrder = null
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val ids = currentShown.map { it.id }.toMutableList()
        val fromIndex = ids.indexOf(from.key)
        val toIndex = ids.indexOf(to.key)
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
        itemsIndexed(shown, key = { _, entry -> entry.id }) { index, entry ->
            ReorderableItem(reorderState, key = entry.id, enabled = reorderable) { isDragging ->
                EntryCard(
                    entry = entry,
                    emphasis = if (reorderable) emphasisFor(index, shown.size) else null,
                    showStatus = showStatus,
                    onClick = { onOpen(entry) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        sheetFor = entry
                    },
                    elevation = if (isDragging) 8f else 1f,
                    handle = if (!reorderable) null else {
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
                                    },
                                ),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_drag),
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    },
                )
            }
        }
        item(key = "hint") {
            Text(
                if (reorderable) "Hold an entry to move it · drag ⋮⋮ to reorder" else "Hold an entry to move it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
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
        )
    }
}

@Composable
private fun EmptyState(tab: Status?, searchActive: Boolean) {
    val (title, hint) = when {
        searchActive -> "No matches" to "Trash isn't searched."
        tab == null -> "Search your entries" to "Type to look through everything."
        tab == Status.INBOX -> "Inbox zero" to "Tap New to get a thought out of your head."
        tab == Status.TODAY -> "Nothing planned for today" to "Hold an entry in the inbox and move it here."
        tab == Status.LATER -> "Nothing parked for later" to "Keepers and someday-maybes go here."
        tab == Status.DONE -> "Nothing done yet" to "Finished entries land here."
        else -> "Trash is empty" to "Trashed entries wait here in case you change your mind."
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(64.dp))
    }
}
