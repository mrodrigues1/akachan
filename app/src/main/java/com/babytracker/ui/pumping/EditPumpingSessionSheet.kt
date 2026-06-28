package com.babytracker.ui.pumping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.ui.component.EditDatePicker
import com.babytracker.ui.component.EditDateTimeRow
import com.babytracker.ui.component.EditTimePicker
import com.babytracker.ui.component.SectionLabel
import com.babytracker.ui.component.SheetSaveButton
import com.babytracker.ui.component.toEditDateLabel
import com.babytracker.ui.component.withEditedDate
import com.babytracker.ui.component.withEditedTime
import com.babytracker.util.formatTime12h
import com.babytracker.util.toRelativeLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
                    text = stringResource(R.string.pumping_edit_title),
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
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.breastfeeding_edit_started))
        Spacer(Modifier.height(8.dp))
        EditDateTimeRow(
            dateLabel = state.editedStart.toEditDateLabel(),
            timeLabel = state.editedStart.formatTime12h(),
            onDateClick = { datePickerFor = EditField.START },
            onTimeClick = { timePickerFor = EditField.START },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.breastfeeding_edit_ended))
        Spacer(Modifier.height(8.dp))
        EditDateTimeRow(
            dateLabel = state.editedEnd?.toEditDateLabel() ?: stringResource(R.string.breastfeeding_edit_set_date),
            timeLabel = state.editedEnd?.formatTime12h() ?: stringResource(R.string.breastfeeding_edit_set_time),
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
            label = { Text(stringResource(R.string.bottle_feed_volume_label)) },
            placeholder = { Text(stringResource(R.string.pumping_optional)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.editedNotes,
            onValueChange = { value -> onFieldChange { it.copy(editedNotes = value) } },
            label = { Text(stringResource(R.string.pumping_notes)) },
            placeholder = { Text(stringResource(R.string.pumping_optional)) },
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
            SheetSaveButton(
                label = stringResource(R.string.save_changes),
                onClick = onSave,
                enabled = state.canSave,
                loading = state.isSaving,
                keepLabelWhileLoading = false,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDeleteRequested,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    datePickerFor?.let { field ->
        val original = if (field == EditField.START) state.editedStart else state.editedEnd ?: state.editedStart
        EditDatePicker(
            initial = original,
            onConfirm = { newDate ->
                val combined = original.withEditedDate(newDate)
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
                val combined = original.withEditedTime(newTime)
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
            text = stringResource(R.string.breastfeeding_edit_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val active = Duration.between(state.editedStart, end)
        .minusMillis(state.original.pausedDurationMs)
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
                        text = stringResource(breast.labelRes()),
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
            text = stringResource(R.string.breastfeeding_delete_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.breastfeeding_delete_message),
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
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
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
                    Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private enum class EditField { START, END }

@Composable
private fun subtitleFor(breast: PumpingBreast, started: Instant): String {
    val date = started.atZone(ZoneId.systemDefault()).toLocalDate()
    return stringResource(
        R.string.breastfeeding_edit_subtitle,
        stringResource(breast.labelRes()),
        date.toRelativeLabel(
            stringResource(R.string.relative_today),
            stringResource(R.string.relative_yesterday),
        ),
    )
}


