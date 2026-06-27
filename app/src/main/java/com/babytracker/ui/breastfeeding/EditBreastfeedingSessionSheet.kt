package com.babytracker.ui.breastfeeding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.usecase.breastfeeding.foldPause
import com.babytracker.ui.component.SheetSaveButton
import com.babytracker.util.formatTime12h
import com.babytracker.util.toRelativeLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBreastfeedingSessionSheet(
    state: EditSheetState,
    onStartChanged: (Instant) -> Unit,
    onEndChanged: (Instant?) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        EditSheetBody(
            state = state,
            onStartChanged = onStartChanged,
            onEndChanged = onEndChanged,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheetBody(
    state: EditSheetState,
    onStartChanged: (Instant) -> Unit,
    onEndChanged: (Instant?) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var datePickerFor by remember { mutableStateOf<EditField?>(null) }
    var timePickerFor by remember { mutableStateOf<EditField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.breastfeeding_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitleFor(state.original.startingSide, state.original.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.breastfeeding_edit_started))
        Spacer(Modifier.height(8.dp))
        FieldRow(
            dateLabel = state.editedStart.toDateLabel(),
            timeLabel = state.editedStart.formatTime12h(),
            onDateClick = { datePickerFor = EditField.START },
            onTimeClick = { timePickerFor = EditField.START },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.breastfeeding_edit_ended))
        Spacer(Modifier.height(8.dp))
        FieldRow(
            dateLabel = state.editedEnd?.toDateLabel() ?: stringResource(R.string.breastfeeding_edit_set_date),
            timeLabel = state.editedEnd?.formatTime12h() ?: stringResource(R.string.breastfeeding_edit_set_time),
            onDateClick = { datePickerFor = EditField.END },
            onTimeClick = { timePickerFor = EditField.END },
            placeholder = state.editedEnd == null,
        )

        Spacer(Modifier.height(12.dp))
        DurationOrError(state = state)

        Spacer(Modifier.height(24.dp))
        SheetSaveButton(
            label = stringResource(R.string.save_changes),
            onClick = onSave,
            enabled = state.canSave,
            loading = state.isSaving,
            keepLabelWhileLoading = false,
        )
    }

    datePickerFor?.let { field ->
        val original = if (field == EditField.START) state.editedStart else state.editedEnd ?: state.editedStart
        EditDatePicker(
            initial = original,
            onConfirm = { newDate ->
                val combined = original.withDate(newDate)
                if (field == EditField.START) onStartChanged(combined) else onEndChanged(combined)
                datePickerFor = null
            },
            onDismiss = { datePickerFor = null },
        )
    }

    timePickerFor?.let { field ->
        val original = if (field == EditField.START) state.editedStart else state.editedEnd ?: state.editedStart
        EditTimePicker(
            initial = original,
            onConfirm = { newTime ->
                val combined = original.withTime(newTime)
                if (field == EditField.START) onStartChanged(combined) else onEndChanged(combined)
                timePickerFor = null
            },
            onDismiss = { timePickerFor = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FieldRow(
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
        FieldCell(
            label = dateLabel,
            onClick = onDateClick,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
        FieldCell(
            label = timeLabel,
            onClick = onTimeClick,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
    }
}

@Composable
private fun FieldCell(
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

@Composable
private fun DurationOrError(state: EditSheetState) {
    val error = state.validationError
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    val end = state.editedEnd
    if (end == null) {
        Text(
            text = stringResource(R.string.breastfeeding_edit_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val (projectedPausedMs, _) = foldPause(state.original, state.editedStart, end)
    val active = Duration.between(state.editedStart, end)
        .minusMillis(projectedPausedMs)
        .coerceAtLeast(Duration.ZERO)
    val durationLabel = when {
        active < Duration.ofMinutes(1) -> stringResource(R.string.duration_less_than_minute)
        active.toHours() > 0 ->
            stringResource(R.string.duration_hours_minutes, active.toHours().toInt(), (active.toMinutes() % 60).toInt())
        else -> stringResource(R.string.duration_minutes, active.toMinutes().toInt())
    }
    Text(
        text = stringResource(R.string.breastfeeding_edit_duration, durationLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

private enum class EditField { START, END }

@Composable
private fun subtitleFor(side: BreastSide, started: Instant): String {
    val sideLabel = if (side == BreastSide.LEFT) {
        stringResource(R.string.breastfeeding_side_left)
    } else {
        stringResource(R.string.breastfeeding_side_right)
    }
    val date = started.atZone(ZoneId.systemDefault()).toLocalDate()
    return stringResource(
        R.string.breastfeeding_edit_subtitle,
        sideLabel,
        date.toRelativeLabel(
            stringResource(R.string.relative_today),
            stringResource(R.string.relative_yesterday),
        ),
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
