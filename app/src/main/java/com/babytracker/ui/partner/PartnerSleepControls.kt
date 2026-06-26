package com.babytracker.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.ui.sleep.SleepTimePickerDialog
import com.babytracker.ui.sleep.labelRes
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Partner sleep controls on the dashboard. Idle: Start Nap / Start Night Sleep pills. Active: a Stop
 * button (shared — works whoever started it) plus an edit affordance for the partner's OWN session.
 */
@Composable
fun PartnerSleepControls(
    state: PartnerSleepUiState,
    onStartNap: () -> Unit,
    onStartNightSleep: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.active
    Column(modifier = modifier.fillMaxWidth()) {
        if (active == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SleepStartButton(
                    text = stringResource(R.string.sleep_start_nap),
                    enabled = !state.isBusy,
                    onClick = onStartNap,
                    modifier = Modifier.weight(1f),
                )
                SleepStartButton(
                    text = stringResource(R.string.sleep_start_night),
                    enabled = !state.isBusy,
                    onClick = onStartNightSleep,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ActiveSleepControls(state = state, active = active, onStop = onStop, onEdit = onEdit)
        }
    }
}

@Composable
private fun ActiveSleepControls(
    state: PartnerSleepUiState,
    active: SleepSnapshot,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (active.startedBy == SleepAuthor.PARTNER.name) {
                        R.string.partner_sleep_started_by_you
                    } else {
                        R.string.partner_sleep_started_by_partner
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStop,
                    enabled = !state.isBusy && !state.stopping,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = if (state.stopping) {
                            stringResource(R.string.partner_sleep_stopping)
                        } else {
                            stringResource(R.string.sleep_stop_session)
                        },
                    )
                }
                if (state.canEditActive) {
                    IconButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.partner_edit_sleep_cd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepStartButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text = text, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Bottom-sheet editor for a partner-owned sleep session (start/end times, type, notes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSleepEditorSheet(
    editor: PartnerSleepEditorState,
    onTypeChange: (SleepType) -> Unit,
    onStartChange: (Instant) -> Unit,
    onEndChange: (Instant?) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.sleep_entry_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepType.entries.forEach { type ->
                    FilterChip(
                        selected = editor.sleepType == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(stringResource(type.labelRes())) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            TimeField(
                label = stringResource(R.string.label_start_time),
                value = editor.startTime.atZone(zone).toLocalTime(),
                onClick = { pickingStart = true },
            )
            Spacer(modifier = Modifier.height(12.dp))
            TimeField(
                label = stringResource(R.string.label_end_time),
                value = editor.endTime?.atZone(zone)?.toLocalTime(),
                onClick = { pickingEnd = true },
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = editor.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.partner_sleep_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
            )

            editor.validationError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = !editor.isSaving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            // Dialogs render as their own windows; keeping them inside the sheet content keeps this
            // composable to a single top-level emitter.
            if (pickingStart) {
                SleepTimePickerDialog(
                    initialTime = editor.startTime.atZone(zone).toLocalTime(),
                    onConfirm = { time ->
                        onStartChange(combineDateAndTime(editor.startTime, time, zone))
                        pickingStart = false
                    },
                    onDismiss = { pickingStart = false },
                )
            }
            if (pickingEnd) {
                val anchor = editor.endTime ?: editor.startTime
                SleepTimePickerDialog(
                    initialTime = anchor.atZone(zone).toLocalTime(),
                    onConfirm = { time ->
                        onEndChange(combineDateAndTime(anchor, time, zone))
                        pickingEnd = false
                    },
                    onDismiss = { pickingEnd = false },
                )
            }
        }
    }
}

@Composable
private fun TimeField(label: String, value: LocalTime?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(
                text = value?.let { "%02d:%02d".format(it.hour, it.minute) }
                    ?: stringResource(R.string.label_in_progress),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

// Keep the original date, replace just the time-of-day (sleep editing on the dashboard is same-day).
private fun combineDateAndTime(anchor: Instant, time: LocalTime, zone: ZoneId): Instant =
    anchor.atZone(zone).toLocalDate().atTime(time).atZone(zone).toInstant()
