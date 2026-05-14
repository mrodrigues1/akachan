package com.babytracker.ui.sleep

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.TimerDisplay
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
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val activeSleepSession by viewModel.activeSleepSession.collectAsStateWithLifecycle()

    val zone = ZoneId.systemDefault()
    val todayEntries = remember(history) {
        val today = LocalDate.now()
        history
            .filter { it.startTime.atZone(zone).toLocalDate() == today && it.endTime != null }
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

    uiState.activeTimePicker?.let { target ->
        val initial = when (target) {
            SleepTimePickerTarget.WAKE -> uiState.wakeTime ?: LocalTime.of(7, 0)
            SleepTimePickerTarget.ENTRY_START -> uiState.entryStartTime
            SleepTimePickerTarget.ENTRY_END -> uiState.entryEndTime
        }
        SleepTimePickerDialog(
            initialTime = initial,
            onConfirm = viewModel::onConfirmTimePicker,
            onDismiss = viewModel::onDismissTimePicker
        )
    }

    uiState.pendingDeleteRecord?.let { record ->
        SleepDeleteConfirmationDialog(
            record = record,
            onDismiss = viewModel::onDismissDelete,
            onConfirm = viewModel::onConfirmDelete
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
                isEditing = uiState.editingRecord != null,
                onTypeChanged = viewModel::onEntryTypeChanged,
                onStartTimeClick = { viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_START) },
                onEndTimeClick = { viewModel.onShowTimePicker(SleepTimePickerTarget.ENTRY_END) },
                onSave = viewModel::onSaveEntry
            )
        }
    }

    Scaffold(
        modifier = modifier,
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
                    TextButton(onClick = onNavigateToSchedule) {
                        Text("Schedule", color = MaterialTheme.colorScheme.secondary)
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
                if (activeSleepSession != null) {
                    ActiveSleepCard(
                        record = activeSleepSession!!,
                        onStop = viewModel::onStopRecord
                    )
                } else {
                    SleepQuickStartRow(
                        onStartNap = { viewModel.onStartRecord(SleepType.NAP) },
                        onStartNightSleep = { viewModel.onStartRecord(SleepType.NIGHT_SLEEP) }
                    )
                }
            }
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
                    onClick = { viewModel.onShowTimePicker(SleepTimePickerTarget.WAKE) }
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
                    SwipeableSleepEntry(
                        record = record,
                        onDeleteRequest = viewModel::onDeleteRequest,
                        onEditRecord = viewModel::onEditRecord
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::onAddEntryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Log Past Sleep", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableSleepEntry(
    record: SleepRecord,
    onDeleteRequest: (SleepRecord) -> Unit,
    onEditRecord: (SleepRecord) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDeleteRequest(record)
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SleepEntryDeleteBackground(dismissState.targetValue) }
    ) {
        Box(
            modifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { onEditRecord(record) },
                onClick = {}
            )
        ) {
            SleepEntryCard(record = record)
        }
    }
}

@Composable
private fun SleepEntryDeleteBackground(targetValue: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        targetValue = if (targetValue == SwipeToDismissBoxValue.EndToStart)
            MaterialTheme.colorScheme.errorContainer
        else
            Color.Transparent,
        label = "deleteBackground"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(color),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete entry",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(end = 20.dp)
        )
    }
}

@Composable
internal fun SleepDeleteConfirmationDialog(
    record: SleepRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete entry?") },
        text = { Text("${record.sleepType.emoji} ${record.sleepType.label} will be removed.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                    text = "🌅 Woke at ${wakeTime.format(formatter)}",
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
                    text = "🌅 Tap to set today's wake time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Set wake time",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                value = if (totalSleep.isZero) "—" else totalSleep.formatDuration()
            )
            SummaryStatColumn(
                label = "Naps",
                value = if (napCount == 0) "—" else napCount.toString()
            )
            SummaryStatColumn(
                label = "Night sleep",
                value = if (nightSleep.isZero) "—" else nightSleep.formatDuration()
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
        Text(text = "🌙", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "No sleep entries yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap below to log a past sleep",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SleepQuickStartRow(
    onStartNap: () -> Unit,
    onStartNightSleep: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onStartNap,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("😴 Start Nap", style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = onStartNightSleep,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("🌙 Start Night Sleep", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActiveSleepCard(record: SleepRecord, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${record.sleepType.emoji} ${record.sleepType.label} in progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TimerDisplay(
                startTimeMillis = record.startTime.toEpochMilli(),
                isRunning = true,
                ringColor = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer
            )
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Stop Session", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun SleepEntryCard(record: SleepRecord) {
    HistoryCard(
        title = record.sleepType.label,
        subtitle = buildString {
            append(record.startTime.formatTime12h())
            if (record.endTime != null) append(" – ${record.endTime.formatTime12h()}")
        },
        trailing = record.endTime?.let { end ->
            Duration.between(record.startTime, end).formatDuration()
        } ?: "In progress",
        badgeEmoji = record.sleepType.emoji,
        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
        trailingColor = MaterialTheme.colorScheme.secondary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSleepEntrySheetContent(
    uiState: SleepUiState,
    onTypeChanged: (SleepType) -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSave: () -> Unit,
    isEditing: Boolean = false,
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
            text = if (isEditing) "Edit Sleep Entry" else "Add Sleep Entry",
            style = MaterialTheme.typography.headlineSmall
        )

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
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }

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

        when {
            uiState.entryError != null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠ ${uiState.entryError}",
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
                        text = "⏱ Duration: ${uiState.entryDurationPreview.formatDuration()}",
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

        val saveLabel = when {
            isEditing && uiState.entryType == SleepType.NAP -> "Update Nap"
            isEditing -> "Update Night Sleep"
            uiState.entryType == SleepType.NAP -> "Save Nap"
            else -> "Save Night Sleep"
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(saveLabel, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context)
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
