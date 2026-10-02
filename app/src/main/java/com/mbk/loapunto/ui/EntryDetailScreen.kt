package com.mbk.loapunto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val createdFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(entry: Entry, onBack: () -> Unit) {
    // Local copy so typing never fights the store; every change is still saved as you go.
    var text by remember(entry.id) { mutableStateOf(entry.text) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val inTrash = entry.status == Status.TRASH

    fun close() {
        if (text.isBlank()) EntryStore.delete(entry.id)
        onBack()
    }
    BackHandler(onBack = ::close)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::close) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { EntryStore.update(entry.id) { it.copy(starred = !it.starred) } }) {
                        Icon(
                            painterResource(if (entry.starred) R.drawable.ic_star else R.drawable.ic_star_outline),
                            contentDescription = if (entry.starred) "Unstar" else "Star",
                            tint = if (entry.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (inTrash) {
                        IconButton(onClick = { EntryStore.update(entry.id) { it.copy(status = Status.INBOX) } }) {
                            Icon(painterResource(R.drawable.ic_restore), contentDescription = "Restore")
                        }
                        IconButton(onClick = { EntryStore.delete(entry.id); onBack() }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete for good")
                        }
                    } else {
                        IconButton(onClick = { EntryStore.update(entry.id) { it.copy(status = Status.TRASH) }; onBack() }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Move to trash")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            TextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    EntryStore.update(entry.id) { it.copy(text = new) }
                },
                placeholder = { Text("Empty thought") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp),
            )
            TriagePanel(entry, onPickDate = { pickingDate = true })
        }
    }

    if (pickingDate) {
        // DatePicker works in UTC midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (entry.due ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        EntryStore.update(entry.id) { it.copy(due = date) }
                    }
                    pickingDate = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun TriagePanel(entry: Entry, onPickDate: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Text("Move to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(Status.INBOX, Status.TODAY, Status.LATER, Status.DONE).forEach { status ->
                    FilterChip(
                        selected = entry.status == status,
                        onClick = { EntryStore.update(entry.id) { it.copy(status = status) } },
                        label = { Text(status.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onPickDate,
                    label = { Text(entry.due?.let { "Due " + formatDue(it) } ?: "Add due date") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null, Modifier.size(18.dp)) },
                )
                if (entry.due != null) {
                    IconButton(onClick = { EntryStore.update(entry.id) { it.copy(due = null) } }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear due date", Modifier.size(18.dp))
                    }
                }
            }
            Text(
                "Captured " + Instant.ofEpochMilli(entry.createdAt).atZone(java.time.ZoneId.systemDefault()).format(createdFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
