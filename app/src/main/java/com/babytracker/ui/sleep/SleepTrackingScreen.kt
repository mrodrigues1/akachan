package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val zone = ZoneId.systemDefault()
    val todayEntries = remember(history) {
        val today = LocalDate.now()
        history
            .filter { it.startTime.atZone(zone).toLocalDate() == today }
            .sortedByDescending { it.startTime }
    }
    val totalSleepToday = remember(todayEntries) {
        todayEntries
            .filter { it.endTime != null }
            .mapNotNull { record -> record.endTime?.let { Duration.between(record.startTime, it) } }
            .fold(Duration.ZERO) { acc, d -> acc + d }
    }
    val napCount = remember(todayEntries) {
        todayEntries.count { it.sleepType == SleepType.NAP }
    }
    val nightSleepDuration = remember(todayEntries) {
        todayEntries
            .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.endTime != null }
            .mapNotNull { record -> record.endTime?.let { Duration.between(record.startTime, it) } }
            .fold(Duration.ZERO) { acc, d -> acc + d }
    }

    var showWakeTimePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showWakeTimePicker) {
        SleepTimePickerDialog(
            initialTime = uiState.wakeTime ?: LocalTime.of(7, 0),
            onConfirm = { time ->
                viewModel.onSetWakeTime(time)
                showWakeTimePicker = false
            },
            onDismiss = { showWakeTimePicker = false }
        )
    }

    if (showStartTimePicker) {
        SleepTimePickerDialog(
            initialTime = uiState.entryStartTime,
            onConfirm = { time ->
                viewModel.onEntryStartTimeChanged(time)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        SleepTimePickerDialog(
            initialTime = uiState.entryEndTime,
            onConfirm = { time ->
                viewModel.onEntryEndTimeChanged(time)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.showEntrySheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissSheet,
            sheetState = sheetState
        ) {
            AddSleepEntrySheetContent(
                uiState = uiState,
                onTypeChanged = viewModel::onEntryTypeChanged,
                onStartTimeClick = { showStartTimePicker = true },
                onEndTimeClick = { showEndTimePicker = true },
                onSave = viewModel::onSaveEntry
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History", color = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
        ) {
            item {
                Text(
                    text = "TODAY'S WAKE TIME",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                WakeTimeChip(
                    wakeTime = uiState.wakeTime,
                    onClick = { showWakeTimePicker = true }
                )
            }
            item {
                SleepSummaryRow(
                    totalSleep = totalSleepToday,
                    napCount = napCount,
                    nightSleep = nightSleepDuration
                )
            }
            item {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (todayEntries.isEmpty()) {
                item { TodayEmptyState() }
            } else {
                items(todayEntries, key = { it.id }) { record ->
                    SleepEntryCard(record = record)
                }
            }
            item {
                Button(
                    onClick = viewModel::onAddEntryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("+ Add Sleep Entry", style = MaterialTheme.typography.titleSmall)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) { Text("History") }
                    OutlinedButton(
                        onClick = onNavigateToSchedule,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) { Text("Schedule") }
                }
            }
        }
    }
}

@Composable
private fun WakeTimeChip(wakeTime: LocalTime?, onClick: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a").withLocale(java.util.Locale.getDefault()) }
    if (wakeTime != null) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83C\uDF05 Woke at ${wakeTime.format(formatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit wake time",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    } else {
        OutlinedCard(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83C\uDF05 Tap to set today's wake time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Set wake time",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SleepSummaryRow(
    totalSleep: Duration,
    napCount: Int,
    nightSleep: Duration
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStatColumn(
                label = "Sleep today",
                value = if (totalSleep.isZero) "\u2014" else totalSleep.formatDuration()
            )
            SummaryStatColumn(
                label = "Naps",
                value = if (napCount == 0) "\u2014" else napCount.toString()
            )
            SummaryStatColumn(
                label = "Night sleep",
                value = if (nightSleep.isZero) "\u2014" else nightSleep.formatDuration()
            )
        }
    }
}

@Composable
private fun SummaryStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "\uD83C\uDF19", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "No sleep entries yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap below to add one",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SleepEntryCard(record: SleepRecord) {
    val isNap = record.sleepType == SleepType.NAP
    HistoryCard(
        title = record.sleepType.label,
        subtitle = buildString {
            append(record.startTime.formatTime12h())
            if (record.endTime != null) append(" \u2013 ${record.endTime.formatTime12h()}")
        },
        trailing = record.endTime?.let { end ->
            Duration.between(record.startTime, end).formatDuration()
        } ?: "In progress",
        badgeEmoji = record.sleepType.emoji,
        badgeColor = if (isNap) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSleepEntrySheetContent(
    uiState: SleepUiState,
    onTypeChanged: (SleepType) -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSave: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a").withLocale(java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Add Sleep Entry",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Type toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SleepType.entries.forEach { type ->
                val isSelected = uiState.entryType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onTypeChanged(type) },
                    label = { Text("${type.emoji} ${type.label}") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (type == SleepType.NAP)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }

        // Start + End time row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "START TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(
                    onClick = onStartTimeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.entryStartTime.format(formatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "END TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(
                    onClick = onEndTimeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.entryEndTime.format(formatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }

        // Duration or error feedback
        when {
            uiState.entryError != null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "\u26A0 ${uiState.entryError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
            uiState.entryDurationPreview != null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "\u23F1 Duration: ${uiState.entryDurationPreview.formatDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }

        // Save button
        val saveLabel = when (uiState.entryType) {
            SleepType.NAP -> "Save Nap"
            SleepType.NIGHT_SLEEP -> "Save Night Sleep"
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.entryType == SleepType.NAP)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(saveLabel, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = timePickerState) }
    )
}
