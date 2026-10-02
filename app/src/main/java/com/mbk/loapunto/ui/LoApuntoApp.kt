package com.mbk.loapunto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private sealed interface Screen {
    data object List : Screen
    data object Sorting : Screen
    data class Detail(val id: String) : Screen
}

@Composable
fun LoApuntoApp(openRequest: String?, onOpenHandled: () -> Unit, onCapture: () -> Unit) {
    val entries by EntryStore.entries.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Status.INBOX) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable { mutableStateOf(false) }
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
        else -> Screen.List
    }
    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
        when (target) {
            is Screen.Detail -> entries.firstOrNull { it.id == target.id }?.let { EntryDetailScreen(it, onBack = { openId = null }) }
            Screen.Sorting -> SortInboxScreen(entries, onExit = { sorting = false })
            Screen.List -> ListScreen(
                entries,
                tab,
                onTab = { tab = it },
                onOpen = { openId = it.id },
                onSort = { sorting = true },
                onCapture = onCapture,
            )
        }
    }
}

/** What swiping does depends on where the entry currently is. A null target deletes for good. */
private class SwipeAction(val label: String, val icon: Int, val color: Color, val target: Status?, val message: String)

private fun swipeRight(status: Status) = when (status) {
    Status.DONE -> SwipeAction("Reopen", R.drawable.ic_restore, SwipeRestore, Status.INBOX, "Back in inbox")
    Status.TRASH -> SwipeAction("Restore", R.drawable.ic_restore, SwipeRestore, Status.INBOX, "Restored to inbox")
    else -> SwipeAction("Done", R.drawable.ic_check, SwipeDone, Status.DONE, "Marked done")
}

private fun swipeLeft(status: Status) =
    if (status == Status.TRASH) SwipeAction("Delete", R.drawable.ic_delete, SwipeTrash, null, "Deleted for good")
    else SwipeAction("Trash", R.drawable.ic_delete, SwipeTrash, Status.TRASH, "Moved to trash")

private fun ordering(status: Status): Comparator<Entry> = when (status) {
    Status.INBOX -> compareBy<Entry> { it.priority }.thenByDescending { it.createdAt }
    Status.TODAY, Status.LATER -> compareBy<Entry> { it.priority }
        .thenBy(nullsLast()) { it.due }
        .thenBy(nullsLast()) { it.dueTime }
        .thenByDescending { it.createdAt }
    Status.DONE, Status.TRASH -> compareByDescending { it.updatedAt }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    entries: List<Entry>,
    tab: Status,
    onTab: (Status) -> Unit,
    onOpen: (Entry) -> Unit,
    onSort: () -> Unit,
    onCapture: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    val searchActive = searching && query.isNotBlank()
    val visible = remember(entries, tab, query, searchActive) {
        if (searchActive) entries.filter { it.status != Status.TRASH && it.text.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
        else entries.filter { it.status == tab }.sortedWith(ordering(tab))
    }
    val inboxCount = entries.count { it.status == Status.INBOX }

    fun apply(entry: Entry, action: SwipeAction) {
        val target = action.target
        if (target == null) EntryStore.delete(entry.id) else EntryStore.update(entry.id) { it.copy(status = target) }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(action.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) EntryStore.upsert(entry)
        }
    }

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
                    if (!searching) IconButton(onClick = { searching = true }) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
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
            if (!searching) StatusTabs(entries, tab, onTab)
            if (!searching && tab == Status.INBOX && inboxCount >= 2) {
                FilledTonalButton(
                    onClick = onSort,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text("Sort inbox one by one ($inboxCount)") }
            }
            if (visible.isEmpty()) {
                EmptyState(if (searching) null else tab, searchActive)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }) { entry ->
                        SwipeableEntry(
                            entry = entry,
                            showStatus = searchActive,
                            onOpen = { onOpen(entry) },
                            onSwiped = { apply(entry, it) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTabs(entries: List<Entry>, selected: Status, onSelect: (Status) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Status.entries.forEach { status ->
            val count = entries.count { it.status == status }
            val showCount = count > 0 && (status == Status.INBOX || status == Status.TODAY)
            FilterChip(
                selected = status == selected,
                onClick = { onSelect(status) },
                label = { Text(if (showCount) "${status.label}  $count" else status.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/**
 * Hand-rolled swipe so it only commits when dragged past half the card.
 * Fling speed is ignored on purpose: quick thumb flicks shouldn't trash anything.
 */
@Composable
private fun SwipeableEntry(
    entry: Entry,
    showStatus: Boolean,
    onOpen: () -> Unit,
    onSwiped: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val right = swipeRight(entry.status)
    val left = swipeLeft(entry.status)
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val threshold = width * 0.5f
    val armed = width > 0 && abs(offset.value) >= threshold
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    // In search the entry can stay listed after its status changes; slide it back in.
    LaunchedEffect(entry.status) { offset.snapTo(0f) }

    Box(modifier.onSizeChanged { width = it.width }) {
        val action = when {
            offset.value > 0f -> right
            offset.value < 0f -> left
            else -> null
        }
        if (action != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CardShape)
                    .background(if (armed) action.color else action.color.copy(alpha = 0.35f))
                    .padding(horizontal = 24.dp),
                contentAlignment = if (action === right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(action.icon), contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (armed) "Release: ${action.label}" else action.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                    onDragStopped = {
                        if (abs(offset.value) >= threshold && width > 0) {
                            val direction = sign(offset.value)
                            offset.animateTo(direction * width)
                            onSwiped(if (direction > 0) right else left)
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ),
        ) {
            EntryCard(entry, showStatus, onOpen)
        }
    }
}

@Composable
private fun EmptyState(tab: Status?, searchActive: Boolean) {
    val (title, hint) = when {
        searchActive -> "No matches" to "Trash isn't searched."
        tab == null -> "Search your entries" to "Type to look through everything."
        tab == Status.INBOX -> "Inbox zero" to "Tap New to get a thought out of your head."
        tab == Status.TODAY -> "Nothing planned for today" to "Open an entry and move it to Today."
        tab == Status.LATER -> "Nothing parked for later" to "Keepers and someday-maybes go here."
        tab == Status.DONE -> "Nothing done yet" to "Drag an entry to the right to finish it."
        else -> "Trash is empty" to "Drag an entry left to trash it. Things here can be restored."
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
