package com.mbk.loapunto.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mbk.loapunto.ClearHistory
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.Nudge
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface Screen {
    data object Lists : Screen
    data object Archive : Screen
    data class Sorting(val walk: Walk) : Screen
    data object Focus : Screen
    data class Detail(val id: String) : Screen
}

/** A block of cards under an optional heading; only a hand-ordered block can be dragged. */
private data class Section(@StringRes val title: Int?, val entries: List<Entry>, val reorderable: Boolean = false)

private val MainTabs = listOf(Status.INBOX, Status.TODAY, Status.LATER)

@Composable
fun LoApuntoApp(openRequest: String?, onOpenHandled: () -> Unit, onCapture: (Status) -> Unit) {
    val entries by EntryStore.entries.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Status.INBOX) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable { mutableStateOf<Walk?>(null) }
    var archive by rememberSaveable { mutableStateOf(false) }
    var focusing by rememberSaveable { mutableStateOf(false) }
    val open = openId?.let { id -> entries.firstOrNull { it.id == id } }

    // The daily nudge is on by default, so ask for notifications once. Android stops asking after a "no".
    val context = LocalContext.current
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!askedNotifications && !granted && Nudge.time(context) != null) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            openId = openRequest
            onOpenHandled()
        }
    }
    LaunchedEffect(openId, open) { if (openId != null && open == null) openId = null }

    val walk = sorting
    val screen = when {
        open != null -> Screen.Detail(open.id)
        walk != null -> Screen.Sorting(walk)
        focusing -> Screen.Focus
        archive -> Screen.Archive
        else -> Screen.Lists
    }
    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
        when (target) {
            is Screen.Detail -> entries.firstOrNull { it.id == target.id }?.let { EntryDetailScreen(it, onBack = { openId = null }) }
            is Screen.Sorting -> SortScreen(entries, target.walk, onExit = { sorting = null })
            Screen.Focus -> FocusScreen(ordered(entries, Status.TODAY), onExit = { focusing = false })
            Screen.Archive -> ArchiveScreen(entries, onOpen = { openId = it.id }, onBack = { archive = false })
            Screen.Lists -> ListScreen(
                entries,
                tab,
                onTab = { tab = it },
                onOpen = { openId = it.id },
                onSort = { sorting = Walk.INBOX },
                onReview = { sorting = Walk.PARKED },
                onFocus = { focusing = true },
                onArchive = { archive = true },
                onCapture = { onCapture(tab) },
            )
        }
    }
}

/** Moves (or, with a null target, deletes) an entry and offers Undo. */
@Composable
fun rememberMover(snackbar: SnackbarHostState): (Entry, Status?) -> Unit {
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    return remember(snackbar, scope, res) {
        { entry, target ->
            if (target == null) EntryStore.delete(entry.id) else EntryStore.move(entry.id, target)
            val message = when (target) {
                null -> res.getString(R.string.deleted_for_good)
                Status.DONE -> res.getString(R.string.marked_done)
                Status.TRASH -> res.getString(R.string.moved_to_trash)
                else -> res.getString(R.string.moved_to, res.getString(target.label))
            }
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(message, res.getString(R.string.undo), duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) EntryStore.upsert(entry)
            }
        }
    }
}

fun ordered(entries: List<Entry>, status: Status): List<Entry> =
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
    onReview: () -> Unit,
    onFocus: () -> Unit,
    onArchive: () -> Unit,
    onCapture: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val move = rememberMover(snackbar)
    val (backUp, restore) = rememberBackup(snackbar)
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var nudgeDialog by rememberSaveable { mutableStateOf(false) }
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
    val todayStart = startOfToday()
    val doneToday = entries.count { it.status == Status.DONE && it.movedAt >= todayStart }
    // Today entries that were put there on an earlier day.
    val leftOvers = if (tab == Status.TODAY && !searching) visible.filter { it.movedAt < todayStart } else emptyList()
    // Later is two piles, so show them as two: what has a day to come back on, and what has none.
    val sections = remember(visible, tab, searchActive) {
        val handOrdered = !searchActive && tab.isOrdered
        val (parked, undated) = visible.partition { it.backOn != null }
        if (searchActive || tab != Status.LATER || parked.isEmpty()) listOf(Section(null, visible, handOrdered))
        else buildList {
            // The ones coming back are ordered by their day; only the dateless pile is yours to order.
            add(Section(R.string.coming_back, parked.sortedBy { it.backOn }))
            if (undated.isNotEmpty()) add(Section(R.string.no_date, undated, handOrdered))
        }
    }
    // Dateless and untouched for a month: offer a walk through them before they become later forever.
    val forgotten = if (tab == Status.LATER && !searching) visible.filter { it.isForgotten() } else emptyList()

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
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close_search))
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.search_everything)) },
                            singleLine = true,
                            colors = transparentFieldColors(),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                        LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    } else {
                        Column {
                            Text(stringResource(R.string.app_name), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                            if (doneToday > 0) {
                                Text(
                                    pluralStringResource(R.plurals.done_today, doneToday, doneToday),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DoneGreen,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = onArchive) {
                            Icon(painterResource(R.drawable.ic_history), contentDescription = stringResource(R.string.history))
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                        }
                    } else if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.new_button)) },
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
                    ) { Text(stringResource(R.string.sort_inbox, inboxCount)) }
                }
                if (tab == Status.TODAY && visible.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onFocus,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_focus), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.one_at_a_time))
                    }
                }
                if (forgotten.isNotEmpty()) ForgottenBar(forgotten.size, onReview)
                if (leftOvers.isNotEmpty()) {
                    LeftOverBar(
                        count = leftOvers.size,
                        onKeep = { EntryStore.keepForToday(leftOvers.map { it.id }) },
                        onLater = { EntryStore.moveAll(leftOvers.map { it.id }, Status.LATER) },
                    )
                }
            }
            if (visible.isEmpty()) {
                EmptyState(if (searching) null else tab, searchActive)
            } else {
                EntryList(sections, showStatus = searchActive, onOpen = onOpen, onMove = move)
            }
        }
    }

    if (settingsOpen) SettingsSheet(
        onNudge = { nudgeDialog = true },
        onBackUp = backUp,
        onRestore = restore,
        onDismiss = { settingsOpen = false },
    )
    if (nudgeDialog) NudgeDialog(onDismiss = { nudgeDialog = false })
}

/** "2 left over [Keep] [Later]": yesterday's Today, without the guilt pile. */
@Composable
private fun LeftOverBar(count: Int, onKeep: () -> Unit, onLater: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.left_over, count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onKeep) { Text(stringResource(R.string.keep)) }
            TextButton(onClick = onLater) { Text(stringResource(R.string.later)) }
        }
    }
}

/** "3 here for over a month · Review": the someday pile, surfaced instead of explained. */
@Composable
private fun ForgottenBar(count: Int, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.here_a_month, count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text(stringResource(R.string.review)) }
        }
    }
}

/** A heading above a block of cards: Later's two halves, visible at a glance. */
@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
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
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.history), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        if (ClearHistory.isOn(LocalContext.current)) Text(
                            stringResource(R.string.history_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Tabs(listOf(Status.DONE, Status.TRASH), entries, tab, onSelect = { tab = it })
            if (visible.isEmpty()) EmptyState(tab, searchActive = false)
            else EntryList(listOf(Section(null, visible)), showStatus = false, onOpen = onOpen, onMove = move)
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
            val label = stringResource(status.label)
            FilterChip(
                selected = status == selected,
                onClick = { onSelect(status) },
                label = {
                    Text(
                        if (count > 0) "$label  $count" else label,
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
 * Cards with long-press for the move sheet. The hand-ordered section also gets a drag grip; the new
 * order is shown locally while dragging and saved when the drag ends.
 */
@Composable
private fun EntryList(
    sections: List<Section>,
    showStatus: Boolean,
    onOpen: (Entry) -> Unit,
    onMove: (Entry, Status?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
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
        item(key = "hint") {
            Text(
                stringResource(if (handIds == null) R.string.hint_hold else R.string.hint_reorder),
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
            onParkUntil = { day ->
                sheetFor = null
                EntryStore.parkUntil(entry.id, day)
            },
        )
    }
}

@Composable
private fun EmptyState(tab: Status?, searchActive: Boolean) {
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
