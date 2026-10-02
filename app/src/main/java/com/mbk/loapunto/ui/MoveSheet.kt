package com.mbk.loapunto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mbk.loapunto.Entry
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Big, thumb-sized destinations. A null target means "delete for good" (only offered from Trash). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(
    entry: Entry,
    onDismiss: () -> Unit,
    onMove: (Status?) -> Unit,
    onEdit: () -> Unit,
    onParkUntil: (LocalDate) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val targets: List<Status?> = buildList {
        addAll(listOf(Status.TODAY, Status.LATER, Status.INBOX, Status.DONE, Status.TRASH).filter { it != entry.status })
        if (entry.status == Status.TRASH) add(null)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text(
                entry.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.currently_in, stringResource(entry.status.label)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            targets.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { target -> MoveButton(target, Modifier.weight(1f)) { onMove(target) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                if (entry.isOpen) TextButton(onClick = { picking = true }) {
                    Icon(painterResource(R.drawable.ic_event), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.later_until))
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.open_and_edit)) }
            }
        }
    }

    if (picking) ParkDatePicker(onDismiss = { picking = false }, onPick = { picking = false; onParkUntil(it) })
}

/** Picks a day from tomorrow on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParkDatePicker(onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val tomorrow = LocalDate.now().plusDays(1)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(tomorrow)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

@Composable
private fun MoveButton(target: Status?, modifier: Modifier, onClick: () -> Unit) {
    val big = modifier.height(56.dp)
    when (target) {
        Status.TODAY -> Button(onClick, big) { Text(stringResource(R.string.today)) }
        // Later is parked (calm blue); Inbox is plain, like a fresh note.
        Status.LATER -> FilledTonalButton(
            onClick,
            big,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) { Text(stringResource(R.string.later)) }
        Status.INBOX -> OutlinedButton(
            onClick,
            big,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) { Text(stringResource(R.string.inbox)) }
        Status.DONE -> Button(
            onClick,
            big,
            colors = ButtonDefaults.buttonColors(containerColor = DoneGreen, contentColor = Color.White),
        ) { Text(stringResource(R.string.done)) }
        Status.TRASH -> OutlinedButton(
            onClick,
            big,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.trash)) }
        null -> Button(
            onClick,
            big,
            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
        ) { Text(stringResource(R.string.delete_for_good)) }
    }
}
