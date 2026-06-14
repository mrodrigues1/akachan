package com.babytracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babytracker.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Reminder settings UI building blocks shared between the global [SettingsScreen] (feeding
 * reminders) and the dedicated `SleepSettingsScreen` (sleep reminders). Extracted so both screens
 * render identical rows without duplication.
 */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeadTimeSegmentedRow(
    enabled: Boolean,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
) {
    val leadTimeLabels = mapOf(
        5 to R.string.settings_lead_time_5,
        10 to R.string.settings_lead_time_10,
        15 to R.string.settings_lead_time_15,
        30 to R.string.settings_lead_time_30,
    )
    val options = LEAD_TIME_MINUTES.map { it to leadTimeLabels.getValue(it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Text(
            text = stringResource(R.string.settings_lead_time_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (minutes, labelRes) ->
                SegmentedButton(
                    selected = selectedMinutes == minutes,
                    onClick = { onSelect(minutes) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuietHoursRow(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    is24Hour: Boolean,
    onStartPicked: (Int) -> Unit,
    onEndPicked: (Int) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val formatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }
    fun formatMinute(minuteOfDay: Int): String =
        LocalTime.ofSecondOfDay((minuteOfDay % MINUTES_PER_DAY) * 60L).format(formatter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Text(
            text = stringResource(R.string.settings_quiet_hours_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showStartPicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_quiet_hours_start),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatMinute(startMinute),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showEndPicker = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_quiet_hours_end),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatMinute(endMinute),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val helperText = if (startMinute == endMinute) {
            stringResource(R.string.settings_quiet_hours_disabled)
        } else {
            stringResource(R.string.settings_quiet_hours_helper)
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(
            initialHour = startMinute / 60,
            initialMinute = startMinute % 60,
            is24Hour = is24Hour,
        )
        TimePickerDialog(
            onDismiss = { showStartPicker = false },
            onConfirm = {
                onStartPicked(state.hour * 60 + state.minute)
                showStartPicker = false
            },
        ) {
            TimePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberTimePickerState(
            initialHour = endMinute / 60,
            initialMinute = endMinute % 60,
            is24Hour = is24Hour,
        )
        TimePickerDialog(
            onDismiss = { showEndPicker = false },
            onConfirm = {
                onEndPicked(state.hour * 60 + state.minute)
                showEndPicker = false
            },
        ) {
            TimePicker(state = state)
        }
    }
}

/**
 * Bottom-sheet content for editing a minutes-based feeding limit (0 disables the limit). Shared by
 * the Feed settings screen for "Max per breast" and "Max total feed".
 */
@Composable
internal fun MinutesEditSheet(
    title: String,
    currentMinutes: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable {
        mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "")
    }
    val minutes = text.toIntOrNull() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .imePadding(),
    ) {
        Text(
            text = "Edit $title",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set to 0 to disable the limit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                if (filtered.length <= 3) text = filtered
            },
            label = { Text("Minutes (0 = disabled)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(onClick = { onSave(minutes) }) { Text("Save") }
        }
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        text = { content() },
    )
}

private val LEAD_TIME_MINUTES = listOf(5, 10, 15, 30)
private const val MINUTES_PER_DAY = 1440
