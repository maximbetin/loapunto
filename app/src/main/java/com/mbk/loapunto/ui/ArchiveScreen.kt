package com.mbk.loapunto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mbk.loapunto.ClearHistory
import com.mbk.loapunto.Entry
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchiveScreen(entries: List<Entry>, onOpen: (Entry) -> Unit, onBack: () -> Unit) {
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
            else EntryList(byDay(visible), showStatus = false, onOpen = onOpen, onMove = move)
        }
    }
}

/**
 * History in days, newest first, each with its own count. Seeing a row of days with numbers on
 * them is the whole reward for finishing things, so it is worth the heading.
 */
@Composable
private fun byDay(entries: List<Entry>): List<Section> =
    entries.groupBy { Instant.ofEpochMilli(it.movedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        .map { (day, ofThatDay) -> Section("${formatDue(day)} \u00b7 ${ofThatDay.size}", ofThatDay) }
