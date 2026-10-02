package com.mbk.loapunto.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mbk.loapunto.Checklist
import com.mbk.loapunto.Entry
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(entry: Entry, onBack: () -> Unit) {
    // Local copies so typing never fights the store; every change is still saved as you go.
    var title by remember(entry.id) { mutableStateOf(entry.text) }
    var notes by remember(entry.id) { mutableStateOf(entry.notes) }
    var editingText by remember(entry.id) { mutableStateOf(false) }
    val notesFocus = remember { FocusRequester() }
    val inTrash = entry.status == Status.TRASH

    fun saveNotes(new: String) {
        notes = new
        EntryStore.update(entry.id) { it.copy(notes = new) }
    }
    fun close() {
        if (title.isBlank() && notes.isBlank()) EntryStore.delete(entry.id)
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
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (inTrash) {
                        IconButton(onClick = { EntryStore.move(entry.id, Status.INBOX) }) {
                            Icon(painterResource(R.drawable.ic_restore), contentDescription = stringResource(R.string.restore))
                        }
                        IconButton(onClick = { EntryStore.delete(entry.id); onBack() }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete_for_good))
                        }
                    } else {
                        IconButton(onClick = { EntryStore.move(entry.id, Status.TRASH); onBack() }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.move_to_trash))
                        }
                    }
                    // Everything is already saved; this is just a clear way out.
                    IconButton(onClick = ::close) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.finished), tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                TextField(
                    value = title,
                    onValueChange = { new ->
                        // Enter in the title jumps to the details instead of adding a line.
                        val head = new.substringBefore('\n')
                        val rest = if ('\n' in new) new.substringAfter('\n') else null
                        title = head
                        EntryStore.update(entry.id) { it.copy(text = head) }
                        if (rest != null) notesFocus.requestFocus()
                    },
                    placeholder = { Text(stringResource(R.string.title)) },
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    keyboardOptions = TextKeyboard,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                val checklist = Checklist.has(notes)
                if (checklist && !editingText) {
                    ChecklistView(notes, onChange = ::saveNotes, onEditText = { editingText = true })
                } else {
                    TextField(
                        value = notes,
                        onValueChange = ::saveNotes,
                        placeholder = { Text(stringResource(R.string.add_details)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        keyboardOptions = TextKeyboard,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).focusRequester(notesFocus),
                    )
                    if (notes.isNotBlank()) {
                        TextButton(
                            onClick = {
                                if (!checklist) saveNotes(Checklist.from(notes))
                                editingText = false
                            },
                            modifier = Modifier.padding(start = 4.dp),
                        ) { Text(stringResource(if (checklist) R.string.show_ticks else R.string.make_checklist)) }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    TriageControls(entry, statuses = listOf(Status.INBOX, Status.TODAY, Status.LATER, Status.DONE))
                    Text(
                        stringResource(
                            R.string.captured_at,
                            Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).format(localFormat("EEEdMMMyyyyHHmm")),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Ticks flip "[ ]" and "[x]" in place; nothing gets reordered or reformatted behind your back. */
@Composable
private fun ChecklistView(notes: String, onChange: (String) -> Unit, onEditText: () -> Unit) {
    var newItem by remember { mutableStateOf("") }
    fun addItem() {
        if (newItem.isNotBlank()) onChange(Checklist.add(notes, newItem))
        newItem = ""
    }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Checklist.parse(notes).forEachIndexed { index, line ->
            val checked = line.checked
            if (checked == null) {
                if (line.text.isNotBlank()) Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable { onChange(Checklist.toggle(notes, index)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                        color = if (checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        TextField(
            value = newItem,
            onValueChange = { newItem = it },
            placeholder = { Text(stringResource(R.string.add_item)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            singleLine = true,
            // Enter adds the item and keeps the keyboard up for the next one.
            keyboardOptions = TextKeyboard.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addItem() }),
            colors = transparentFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onEditText) { Text(stringResource(R.string.edit_as_text)) }
    }
}

/** Status and due date/reminder controls, shared by the detail and sorting screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageControls(entry: Entry, statuses: List<Status>) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (statuses.isNotEmpty()) {
        ControlLabel(stringResource(R.string.move_to))
        ChipRow {
            statuses.forEach { status ->
                ChoiceChip(entry.status == status, stringResource(status.label)) { EntryStore.move(entry.id, status) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { pickingDate = true },
            label = { Text(entry.due?.let { stringResource(R.string.due, formatDue(it)) } ?: stringResource(R.string.add_due_date)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null, Modifier.size(18.dp)) },
        )
        if (entry.due != null) {
            AssistChip(
                onClick = { pickingTime = true },
                label = { Text(entry.dueTime?.let { stringResource(R.string.remind_at, formatTime(it)) } ?: stringResource(R.string.remind_me)) },
            )
            IconButton(onClick = { EntryStore.update(entry.id) { it.copy(due = null, dueTime = null) } }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_due_date), Modifier.size(18.dp))
            }
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
                }) { Text(stringResource(R.string.set)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    if (pickingTime) {
        val initial = entry.dueTime ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.remind_me_at)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    EntryStore.update(entry.id) { it.copy(dueTime = LocalTime.of(state.hour, state.minute)) }
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    pickingTime = false
                }) { Text(stringResource(R.string.set)) }
            },
            dismissButton = {
                Row {
                    if (entry.dueTime != null) TextButton(onClick = {
                        EntryStore.update(entry.id) { it.copy(dueTime = null) }
                        pickingTime = false
                    }) { Text(stringResource(R.string.no_reminder)) }
                    TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun ControlLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ChoiceChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = Color.Transparent,
        ),
    )
}
