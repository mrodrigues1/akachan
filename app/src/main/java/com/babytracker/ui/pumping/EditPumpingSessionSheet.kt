package com.babytracker.ui.pumping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.displayName
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
fun EditPumpingSessionSheet(
    state: EditPumpingSheetState,
    onFieldChange: ((EditPumpingSheetState) -> EditPumpingSheetState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
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
        EditPumpingSheetBody(
            state = state,
            onFieldChange = onFieldChange,
            onDismiss = onDismiss,
            onSave = onSave,
            onDeleteRequested = onDeleteRequested,
            onDeleteConfirmed = onDeleteConfirmed,
            onDeleteCancelled = onDeleteCancelled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPumpingSheetBody(
    state: EditPumpingSheetState,
    onFieldChange: ((EditPumpingSheetState) -> EditPumpingSheetState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
) {
    var datePickerFor by remember { mutableStateOf<EditField?>(null) }
    var timePickerFor by remember { mutableStateOf<EditField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Edit session",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitleFor(state.original.breast, state.original.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("STARTED")
        Spacer(Modifier.height(8.dp))
        FieldRow(
            dateLabel = state.editedStart.toDateLabel(),
            timeLabel = state.editedStart.formatTime12h(),
            onDateClick = { datePickerFor = EditField.START },
            onTimeClick = { timePickerFor = EditField.START },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("ENDED")
        Spacer(Modifier.height(8.dp))
        FieldRow(
            dateLabel = state.editedEnd?.toDateLabel() ?: "Set date",
            timeLabel = state.editedEnd?.formatTime12h() ?: "Set time",
            onDateClick = { datePickerFor = EditField.END },
            onTimeClick = { timePickerFor = EditField.END },
            placeholder = state.editedEnd == null,
        )

        Spacer(Modifier.height(12.dp))
        PumpingDurationOrError(state = state)

        Spacer(Modifier.height(20.dp))
        SectionLabel("BREAST")
        Spacer(Modifier.height(8.dp))
        BreastPillRow(
            selected = state.editedBreast,
            onSelect = { value -> onFieldChange { it.copy(editedBreast = value) } },
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.editedVolumeMl,
            onValueChange = { input ->
                onFieldChange { it.copy(editedVolumeMl = input.filter { c -> c.isDigit() }) }
            },
            label = { Text("Volume (mL)") },
            placeholder = { Text("Optional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.editedNotes,
            onValueChange = { value -> onFieldChange { it.copy(editedNotes = value) } },
            label = { Text("Notes") },
            placeholder = { Text("Optional") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        if (state.deleteConfirm) {
            DeleteConfirmRow(
                isDeleting = state.isDeleting,
                onCancel = onDeleteCancelled,
                onConfirm = onDeleteConfirmed,
            )
        } else {
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save changes", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDeleteRequested,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    datePickerFor?.let { field ->
        val original = if (field == EditField.START) state.editedStart else state.editedEnd ?: state.editedStart
        EditDatePicker(
            initial = original,
            onConfirm = { newDate ->
                val combined = original.withDate(newDate)
                if (field == EditField.START) {
                    onFieldChange { it.copy(editedStart = combined) }
                } else {
                    onFieldChange { it.copy(editedEnd = combined) }
                }
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
                if (field == EditField.START) {
                    onFieldChange { it.copy(editedStart = combined) }
                } else {
                    onFieldChange { it.copy(editedEnd = combined) }
                }
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
private fun PumpingDurationOrError(state: EditPumpingSheetState) {
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
            text = "Session in progress",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val active = Duration.between(state.editedStart, end)
        .minusMillis(state.original.pausedDurationMs)
        .coerceAtLeast(Duration.ZERO)
    Text(
        text = "Duration: ${formatDurationShort(active)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BreastPillRow(
    selected: PumpingBreast,
    onSelect: (PumpingBreast) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PumpingBreast.entries.forEach { breast ->
            FilterChip(
                selected = selected == breast,
                onClick = { onSelect(breast) },
                label = {
                    Text(
                        text = breast.displayName(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun DeleteConfirmRow(
    isDeleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Delete this session?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "It can't be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = !isDeleting,
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
        shape = MaterialTheme.shapes.large,
    )
}

private enum class EditField { START, END }

private fun subtitleFor(breast: PumpingBreast, started: Instant): String {
    val date = started.atZone(ZoneId.systemDefault()).toLocalDate()
    return "${breast.displayName()} · ${date.toRelativeLabel()}"
}

private fun Instant.toDateLabel(): String {
    val date = atZone(ZoneId.systemDefault()).toLocalDate()
    val relative = date.toRelativeLabel()
    return if (relative == "Today" || relative == "Yesterday") relative
    else DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(date)
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

private fun formatDurationShort(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = (duration.toMinutes() % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "less than 1m"
    }
}
