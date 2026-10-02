package com.mbk.loapunto.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mbk.loapunto.EntryStore
import com.mbk.loapunto.Nudge
import com.mbk.loapunto.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** Back up to and restore from a file of your choice. Returns (backUp, restore). */
@Composable
fun rememberBackup(snackbar: SnackbarHostState): Pair<() -> Unit, () -> Unit> {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    fun say(text: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(text)
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val count = EntryStore.entries.value.size
            val json = EntryStore.exportJson()
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            say(
                if (saved) res.getQuantityString(R.plurals.backed_up, count, count)
                else res.getString(R.string.backup_failed),
            )
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val count = runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                EntryStore.importJson(json)
            }.getOrNull()
            say(
                if (count != null) res.getQuantityString(R.plurals.restored, count, count)
                else res.getString(R.string.restore_failed),
            )
        }
    }
    return { save.launch("loapunto-${LocalDate.now()}.json") } to
        { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NudgeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { Nudge.time(context) }
    val initial = current ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.daily_nudge)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.nudge_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Nudge.set(context, LocalTime.of(state.hour, state.minute))
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                onDismiss()
            }) { Text(stringResource(R.string.set)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = {
                    Nudge.set(context, null)
                    onDismiss()
                }) { Text(stringResource(R.string.turn_off)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
