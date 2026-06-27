package com.babytracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.babytracker.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun EditDateTimeRow(
    dateLabel: String,
    timeLabel: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    placeholder: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditDateTimeCell(
            label = dateLabel,
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
        EditDateTimeCell(
            label = timeLabel,
            onClick = onTimeClick,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
    }
}

@Composable
private fun EditDateTimeCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditDatePicker(
    initial: Instant,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val localDate = initial.atZone(zone).toLocalDate()
    val initialEpochMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                onConfirm(picked)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTimePicker(
    initial: Instant,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = initial.atZone(ZoneId.systemDefault()).toLocalTime()
    val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun Instant.toEditDateLabel(): String {
    val date = atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.relative_today)
        today.minusDays(1) -> stringResource(R.string.relative_yesterday)
        else -> DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
    }
}

internal fun Instant.withEditedDate(date: LocalDate): Instant {
    val zone = ZoneId.systemDefault()
    val time = atZone(zone).toLocalTime()
    return LocalDateTime.of(date, time).atZone(zone).toInstant()
}

internal fun Instant.withEditedTime(time: LocalTime): Instant {
    val zone = ZoneId.systemDefault()
    val existingDate = atZone(zone).toLocalDate()
    return LocalDateTime.of(existingDate, time).atZone(zone).toInstant()
}
