package com.babytracker.ui.inventory

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventorySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Milk Stash Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        InventorySettingsContent(
            state = state,
            is24Hour = is24Hour,
            onExpirationEnabledChanged = viewModel::onExpirationEnabledChanged,
            onDaysChanged = viewModel::onDaysChanged,
            onNotifEnabledChanged = viewModel::onNotifEnabledChanged,
            onNotifTimeClicked = viewModel::onTimePickerOpen,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    if (state.showTimePicker) {
        InventorySettingsTimePickerDialog(
            minuteOfDay = state.notificationTimeMinutes,
            is24Hour = is24Hour,
            onConfirm = viewModel::onNotifTimeChanged,
            onDismiss = viewModel::onTimePickerDismiss,
        )
    }
}

@Composable
internal fun InventorySettingsContent(
    state: InventorySettingsUiState,
    is24Hour: Boolean,
    onExpirationEnabledChanged: (Boolean) -> Unit,
    onDaysChanged: (String) -> Unit,
    onNotifEnabledChanged: (Boolean) -> Unit,
    onNotifTimeClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        SettingsSectionHeader(text = "EXPIRATION TRACKING")
        InventorySettingsSwitchRow(
            label = "Expiration tracking",
            description = "Flag stored milk before it reaches the limit",
            checked = state.isExpirationEnabled,
            onCheckedChange = onExpirationEnabledChanged,
        )

        if (state.isExpirationEnabled) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            OutlinedTextField(
                value = state.expirationDays,
                onValueChange = onDaysChanged,
                label = { Text("Expires after") },
                suffix = { Text("days") },
                isError = state.validationError != null,
                supportingText = state.validationError?.let { error ->
                    { Text(error) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            HorizontalDivider()
            SettingsSectionHeader(text = "NOTIFICATIONS")
            InventorySettingsSwitchRow(
                label = "Expiration reminders",
                description = "Send a daily reminder for milk expiring today",
                checked = state.isNotificationEnabled,
                onCheckedChange = onNotifEnabledChanged,
            )

            if (state.isNotificationEnabled) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                NotificationTimeRow(
                    minuteOfDay = state.notificationTimeMinutes,
                    is24Hour = is24Hour,
                    onClick = onNotifTimeClicked,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun InventorySettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun NotificationTimeRow(
    minuteOfDay: Int,
    is24Hour: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Notify at", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Daily expiration check",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Icon(Icons.Outlined.Schedule, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(formatMinuteOfDay(minuteOfDay, is24Hour))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySettingsTimePickerDialog(
    minuteOfDay: Int,
    is24Hour: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val safeMinuteOfDay = minuteOfDay.coerceIn(MIN_MINUTE_OF_DAY, MAX_MINUTE_OF_DAY)
    val pickerState = rememberTimePickerState(
        initialHour = safeMinuteOfDay / MINUTES_PER_HOUR,
        initialMinute = safeMinuteOfDay % MINUTES_PER_HOUR,
        is24Hour = is24Hour,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(pickerState.hour * MINUTES_PER_HOUR + pickerState.minute)
                },
            ) {
                Text("OK")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Reminder time",
                    style = MaterialTheme.typography.titleMedium,
                )
                TimePicker(state = pickerState)
            }
        },
    )
}

private fun formatMinuteOfDay(
    minuteOfDay: Int,
    is24Hour: Boolean,
): String {
    val safeMinuteOfDay = minuteOfDay.coerceIn(MIN_MINUTE_OF_DAY, MAX_MINUTE_OF_DAY)
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return LocalTime
        .ofSecondOfDay(safeMinuteOfDay.toLong() * SECONDS_PER_MINUTE)
        .format(DateTimeFormatter.ofPattern(pattern))
}

private const val MIN_MINUTE_OF_DAY = 0
private const val MAX_MINUTE_OF_DAY = 1439
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60L
