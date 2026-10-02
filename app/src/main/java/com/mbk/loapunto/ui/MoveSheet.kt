package com.mbk.loapunto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mbk.loapunto.Entry
import com.mbk.loapunto.Status

/** Big, thumb-sized destinations. A null target means "delete for good" (only offered from Trash). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(entry: Entry, onDismiss: () -> Unit, onMove: (Status?) -> Unit, onEdit: () -> Unit) {
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
                "Currently in ${entry.status.label}",
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
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Open and edit") }
        }
    }
}

@Composable
private fun MoveButton(target: Status?, modifier: Modifier, onClick: () -> Unit) {
    val big = modifier.height(56.dp)
    when (target) {
        Status.TODAY -> Button(onClick, big) { Text("Today") }
        Status.LATER, Status.INBOX -> FilledTonalButton(onClick, big) { Text(target.label) }
        Status.DONE -> Button(
            onClick,
            big,
            colors = ButtonDefaults.buttonColors(containerColor = DoneGreen, contentColor = Color.White),
        ) { Text("Done") }
        Status.TRASH -> OutlinedButton(
            onClick,
            big,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Trash") }
        null -> Button(
            onClick,
            big,
            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
        ) { Text("Delete for good") }
    }
}
