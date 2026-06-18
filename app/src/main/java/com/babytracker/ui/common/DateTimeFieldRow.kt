package com.babytracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.babytracker.R
import com.babytracker.util.formatTime12h
import com.babytracker.util.toRelativeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Labelled date + time row used by entry sheets (milk bag, bottle feed).
 * Two tappable [FieldCell]s open a date and a time picker respectively, both editing the same [Instant].
 */
@Composable
fun DateTimeFieldRow(
    label: String,
    timestamp: Instant,
    onChange: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: FieldAccent? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent?.accent ?: MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldCell(
                label = timestamp.toDateLabel(),
                onClick = { showDatePicker = true },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            FieldCell(
                label = timestamp.formatTime12h(),
                onClick = { showTimePicker = true },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showDatePicker) {
        PickerAccentTheme(accent) {
            EditDatePicker(
                initial = timestamp,
                onConfirm = { newDate ->
                    onChange(timestamp.withDate(newDate))
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )
        }
    }

    if (showTimePicker) {
        PickerAccentTheme(accent) {
            EditTimePicker(
                initial = timestamp,
                onConfirm = { newTime ->
                    onChange(timestamp.withTime(newTime))
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false },
            )
        }
    }
}

/**
 * Scopes the picker dialogs to a section [accent]. Remaps `primary` (selected day, clock
 * hand/selector, OK/Cancel), `primaryContainer` (the selected hour/minute field box) and
 * `tertiaryContainer` (the AM/PM period selector) so the whole dialog reads in one palette.
 * No-op when [accent] is null — milk-bag/bottle-feed pickers keep the default M3 colours.
 */
@Composable
private fun PickerAccentTheme(accent: FieldAccent?, content: @Composable () -> Unit) {
    if (accent == null) {
        content()
    } else {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = accent.accent,
                onPrimary = accent.onAccent,
                primaryContainer = accent.container,
                onPrimaryContainer = accent.onContainer,
                tertiaryContainer = accent.accent,
                onTertiaryContainer = accent.onAccent,
            ),
            content = content,
        )
    }
}

@Composable
private fun FieldCell(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDatePicker(
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
private fun EditTimePicker(
    initial: Instant,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = initial.atZone(ZoneId.systemDefault()).toLocalTime()
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = false,
    )
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
private fun Instant.toDateLabel(): String {
    val date = atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.relative_today)
        today.minusDays(1) -> stringResource(R.string.relative_yesterday)
        else -> DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
    }
}

private fun Instant.withDate(date: LocalDate): Instant {
    val zone = ZoneId.systemDefault()
    val time = atZone(zone).toLocalTime()
    return LocalDateTime.of(date, time).atZone(zone).toInstant()
}

private fun Instant.withTime(time: LocalTime): Instant {
    val zone = ZoneId.systemDefault()
    val existingDate = atZone(zone).toLocalDate()
    return LocalDateTime.of(existingDate, time).atZone(zone).toInstant()
}
