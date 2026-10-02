package com.mbk.loapunto.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mbk.loapunto.Checklist
import com.mbk.loapunto.Entry
import com.mbk.loapunto.R
import com.mbk.loapunto.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val CardShape = RoundedCornerShape(18.dp)

/** A date pattern in the phone's language and word order, from a skeleton like "EEEMMMd". */
fun localFormat(skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton))

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(time: LocalTime): String = time.format(timeFormat)

@Composable
fun formatDue(date: LocalDate, time: LocalTime? = null): String {
    val today = LocalDate.now()
    val day = when (date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> date.format(localFormat("EEEMMMd"))
    }
    return if (time == null) day else "$day ${formatTime(time)}"
}

fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun isOverdue(date: LocalDate, time: LocalTime?): Boolean =
    if (time == null) date.isBefore(LocalDate.now()) else LocalDateTime.of(date, time).isBefore(LocalDateTime.now())

@Composable
fun formatAge(millis: Long): String {
    val now = System.currentTimeMillis()
    return if (now - millis < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.just_now)
    else DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** 1 for the top of a list fading to a faint 0.15 at the bottom; position, not urgency. */
fun emphasisFor(index: Int, count: Int): Float =
    if (count <= 1) 1f else 1f - 0.85f * index / (count - 1)

/**
 * @param emphasis strength of the left accent bar, or null for lists that aren't hand-ordered.
 * @param handle trailing drag grip, supplied by reorderable lists.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCard(
    entry: Entry,
    emphasis: Float?,
    showStatus: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Float = 1f,
    handle: (@Composable () -> Unit)? = null,
) {
    val done = entry.status == Status.DONE
    // Untouched for a month: faded, but still clearly there.
    // Parked until a later day: also faded, it isn't for now.
    val fade = if (entry.isStale() || entry.backOn != null) 0.55f else 1f
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        if (emphasis == null) Color.Transparent
                        else MaterialTheme.colorScheme.primary.copy(alpha = emphasis),
                    ),
            )
            Column(
                Modifier
                    .weight(1f)
                    .alpha(fade)
                    .padding(start = 14.dp, end = if (handle == null) 16.dp else 0.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (done) 0.55f else 1f),
                )
                if (entry.notes.isNotBlank()) {
                    Text(
                        Checklist.preview(entry.notes),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatAge(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.due?.let { due ->
                        val overdue = !done && isOverdue(due, entry.dueTime)
                        Pill(
                            text = stringResource(if (overdue) R.string.overdue else R.string.due, formatDue(due, entry.dueTime)),
                            container = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            content = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    entry.backOn?.let { day ->
                        Pill(
                            stringResource(R.string.back_on, formatDue(day)),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    if (showStatus) {
                        Pill(
                            stringResource(entry.status.label),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (handle != null) Box(Modifier.align(Alignment.CenterVertically)) { handle() }
        }
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
