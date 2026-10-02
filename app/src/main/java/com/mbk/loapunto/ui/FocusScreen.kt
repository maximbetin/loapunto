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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mbk.loapunto.Checklist
import com.mbk.loapunto.Entry
import com.mbk.loapunto.R
import com.mbk.loapunto.Status

/** Today, one entry at a time: only the next thing is on screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(today: List<Entry>, onExit: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val move = rememberMover(snackbar)
    var skipped by rememberSaveable { mutableStateOf(listOf<String>()) }
    val queue = today.filter { it.id !in skipped }
    val current = queue.firstOrNull()

    BackHandler(onBack = onExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onExit) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.back)) }
                },
                title = {},
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = current,
            contentKey = { it?.id },
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
            label = "focusCard",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { entry ->
            if (entry == null) TodayClear(skipped.size, onExit)
            else FocusCard(
                entry,
                onDone = { move(entry, Status.DONE) },
                onLater = { move(entry, Status.LATER) },
                onSkip = { skipped = skipped + entry.id },
            )
        }
    }
}

@Composable
private fun FocusCard(entry: Entry, onDone: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            Text(entry.text, fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 38.sp)
            if (entry.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    Checklist.preview(entry.notes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = DoneGreen, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.done), fontSize = 18.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onLater,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.later)) }
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.not_now))
                }
            }
        }
    }
}

@Composable
private fun TodayClear(skippedCount: Int, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (skippedCount == 0) stringResource(R.string.today_clear)
            else pluralStringResource(R.plurals.still_in_today, skippedCount, skippedCount),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit) { Text(stringResource(R.string.back_to_list)) }
    }
}
