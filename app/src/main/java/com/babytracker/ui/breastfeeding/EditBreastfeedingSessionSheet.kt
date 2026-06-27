package com.babytracker.ui.breastfeeding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.usecase.breastfeeding.foldPause
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
                val combined = original.withEditedDate(newDate)
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
                val combined = original.withEditedTime(newTime)
                if (field == EditField.START) onStartChanged(combined) else onEndChanged(combined)
                timePickerFor = null
            },
            onDismiss = { timePickerFor = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    SectionLabel(
        text = text,
        color = MaterialTheme.colorScheme.primary,
    )
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
