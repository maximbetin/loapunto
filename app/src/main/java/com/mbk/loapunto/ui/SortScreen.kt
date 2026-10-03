package com.mbk.loapunto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.R
import com.mbk.loapunto.Status

/**
 * The two one-at-a-time walks. An entry leaves the queue the moment it stops matching, and every
 * button on the card touches [Entry.updatedAt], so pressing anything moves the walk along.
 */
enum class Walk {
    /** The inbox, oldest first, until everything has a home. */
    INBOX,

    /** The someday pile: parked with no day, untouched for a month. Still want this? */
    PARKED,
    ;

    fun queue(entries: List<Entry>): List<Entry> = when (this) {
        INBOX -> entries.filter { it.status == Status.INBOX }.sortedBy { it.createdAt }
        PARKED -> entries.filter { it.isForgotten() }.sortedBy { it.updatedAt }
    }
}

/** Walks through one queue, one entry at a time, until it is empty. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortScreen(entries: List<Entry>, walk: Walk, onExit: () -> Unit) {
    var skipped by rememberSaveable { mutableStateOf(listOf<String>()) }
    val queue = walk.queue(entries).filterNot { it.id in skipped }
    val total = rememberSaveable { queue.size }
    val current = queue.firstOrNull()
    val handled = (total - queue.size).coerceAtLeast(0)

    BackHandler(onBack = onExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onExit) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.stop_sorting)) }
                },
                title = {
                    Text(
                        if (current == null) stringResource(if (walk == Walk.INBOX) R.string.sorted else R.string.reviewed)
                        else stringResource(R.string.n_of_total, handled + 1, total),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LinearProgressIndicator(
                progress = { if (total == 0) 1f else handled / total.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            AnimatedContent(
                targetState = current,
                contentKey = { it?.id },
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
                label = "sortCard",
                modifier = Modifier.weight(1f),
            ) { entry ->
                if (entry == null) AllSorted(walk, skipped.size, onExit)
                else SortCard(entry, walk, onSkip = { skipped = skipped + entry.id })
            }
        }
    }
}

@Composable
private fun SortCard(shown: Entry, walk: Walk, onSkip: () -> Unit) {
    // AnimatedContent hands us the entry as it was when it appeared; read the live copy for chips.
    val live by EntryStore.entries.collectAsStateWithLifecycle()
    val entry = live.firstOrNull { it.id == shown.id } ?: shown
    var text by remember(entry.id) { mutableStateOf(entry.text) }
    fun send(status: Status) = EntryStore.move(entry.id, status)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                TextField(
                    value = text,
                    onValueChange = { typed ->
                        // The title is one line; details are added from the entry itself.
                        val new = typed.replace('\n', ' ')
                        text = new
                        EntryStore.update(entry.id) { it.copy(text = new) }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                    keyboardOptions = TextKeyboard,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(4.dp),
                )
                Text(
                    if (walk == Walk.PARKED) stringResource(R.string.parked_tidy, formatAge(entry.movedAt))
                    else stringResource(R.string.captured_tidy, formatAge(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            TriageControls(entry, statuses = emptyList())
        }
        Column(
            Modifier.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val parked = walk == Walk.PARKED
            Text(
                stringResource(if (parked) R.string.still_want_this else R.string.where_does_it_go),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { send(Status.TODAY) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.today)) }
                // Out of the inbox it goes to Later; out of Later it goes back to the inbox to be sorted.
                FilledTonalButton(
                    onClick = { send(if (parked) Status.INBOX else Status.LATER) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(if (parked) R.string.inbox else R.string.later)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { send(Status.DONE) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.already_done)) }
                OutlinedButton(
                    onClick = { send(Status.TRASH) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.trash)) }
            }
            // Keeping it parked counts as having looked at it: that is what buys another month.
            TextButton(
                onClick = { if (parked) send(Status.LATER) else onSkip() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(if (parked) R.string.keep_parked else R.string.skip_for_now)) }
        }
    }
}

@Composable
private fun AllSorted(walk: Walk, skippedCount: Int, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.head_cleared), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(8.dp))
        Text(
            if (walk == Walk.PARKED) stringResource(R.string.all_reviewed)
            else if (skippedCount == 0) stringResource(R.string.all_sorted)
            else pluralStringResource(R.plurals.skipped_waiting, skippedCount, skippedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit) { Text(stringResource(R.string.back_to_list)) }
    }
}
