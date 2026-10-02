package com.mbk.loapunto.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mbk.loapunto.Entry
import com.mbk.loapunto.Priority
import com.mbk.loapunto.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val CardShape = RoundedCornerShape(18.dp)

private val dueFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

fun formatTime(time: LocalTime): String = time.format(timeFormat)

fun formatDue(date: LocalDate, time: LocalTime? = null): String {
    val today = LocalDate.now()
    val day = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dueFormat)
    }
    return if (time == null) day else "$day ${formatTime(time)}"
}

fun isOverdue(date: LocalDate, time: LocalTime?): Boolean =
    if (time == null) date.isBefore(LocalDate.now()) else LocalDateTime.of(date, time).isBefore(LocalDateTime.now())

fun formatAge(millis: Long): String {
    val now = System.currentTimeMillis()
    return if (now - millis < DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
fun EntryCard(entry: Entry, showStatus: Boolean, onClick: () -> Unit) {
    val done = entry.status == Status.DONE
    Card(
        onClick = onClick,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // First line reads as a title, the rest as body.
                val firstBreak = entry.text.indexOf('\n')
                val detailColor = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(if (firstBreak < 0) entry.text else entry.text.substring(0, firstBreak))
                        }
                        // Detail lines: smaller and quieter, so the title stays scannable.
                        if (firstBreak >= 0) withStyle(SpanStyle(fontSize = 14.sp, color = detailColor)) {
                            append(entry.text.substring(firstBreak))
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (done) 0.55f else 1f),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatAge(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (entry.priority) {
                    Priority.HIGH -> Pill("High", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                    Priority.LOW -> Pill("Low", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    Priority.NORMAL -> Unit
                }
                entry.due?.let { due ->
                    val overdue = !done && isOverdue(due, entry.dueTime)
                    Pill(
                        text = (if (overdue) "Overdue · " else "Due ") + formatDue(due, entry.dueTime),
                        container = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        content = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (showStatus) {
                    Pill(
                        entry.status.label,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
