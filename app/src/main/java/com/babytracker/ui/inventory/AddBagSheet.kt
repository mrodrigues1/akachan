package com.babytracker.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBagSheet(
    state: AddBagSheetState,
    onFieldChange: ((AddBagSheetState) -> AddBagSheetState) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Add milk bag",
    confirmLabel: String = "Save bag",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            if (!state.isSaving) onDismiss()
        },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            CollectionDateRow(
                date = state.collectionDate,
                onChange = { newDate -> onFieldChange { it.copy(collectionDate = newDate) } },
                enabled = !state.isSaving,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.volumeMl,
                onValueChange = { input ->
                    onFieldChange {
                        it.copy(
                            volumeMl = input.filter { c -> c.isDigit() },
                            validationError = null,
                        )
                    }
                },
                label = { Text("Volume (mL)") },
                singleLine = true,
                isError = state.validationError != null,
                enabled = !state.isSaving,
                supportingText = { state.validationError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = { value -> onFieldChange { it.copy(notes = value) } },
                label = { Text("Notes (optional)") },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CollectionDateRow(
    date: Instant,
    onChange: (Instant) -> Unit,
    enabled: Boolean = true,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "COLLECTION DATE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldCell(
                label = date.toDateLabel(),
                onClick = { showDatePicker = true },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            FieldCell(
                label = date.formatTime12h(),
                onClick = { showTimePicker = true },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showDatePicker) {
        EditDatePicker(
            initial = date,
            onConfirm = { newDate ->
                onChange(date.withDate(newDate))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        EditTimePicker(
            initial = date,
            onConfirm = { newTime ->
                onChange(date.withTime(newTime))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
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
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = false,
    )
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
