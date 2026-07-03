package com.babytracker.ui.sleep

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.component.CueQuickTapRow
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.NapIcon
import com.babytracker.ui.component.SleepIcon
import com.babytracker.ui.component.TimerDisplay
import com.babytracker.ui.component.formatElapsedAsClock
import com.babytracker.ui.theme.Blue200
import com.babytracker.ui.theme.Blue900
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSleepSession by viewModel.activeSleepSession.collectAsStateWithLifecycle()
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()

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
                onDateChanged = viewModel::onEntryDateChanged,
                onSave = viewModel::onSaveEntry
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.sleep_cd_history))
                    }
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.sleep_cd_schedule))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.sleep_cd_settings))
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp, top = 8.dp)
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
            if (activeSleepSession == null) {
                item {
                    TodayContextCard(
                        summary = uiState.lastSleepSummary,
                        wakeTime = uiState.wakeTime,
                        totalSleep = todayStats.totalSleep,
                        napCount = todayStats.napCount,
                        nightSleep = todayStats.nightSleep,
                        onWakeTimeClick = { viewModel.onShowTimePicker(SleepTimePickerTarget.WAKE) },
                    )
                }
            }
            item {
                SleepRecommendationSection(
                    state = uiState.sleepPrediction,
                )
            }
            item {
                CueQuickTapRow(onCueTapped = viewModel::onCueTapped)
            }
            item {
                Text(
                    text = stringResource(R.string.sleep_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (todayStats.entries.isEmpty()) {
                item { TodayEmptyState() }
            } else {
                items(todayStats.entries, key = { it.id }) { record ->
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(stringResource(R.string.sleep_log_past), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun TodayContextCard(
    summary: LastSleepSummaryState,
    wakeTime: LocalTime?,
    totalSleep: Duration,
    napCount: Int,
    nightSleep: Duration,
    onWakeTimeClick: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a").withLocale(java.util.Locale.getDefault()) }
    val wakeTimeLabel = wakeTime?.format(formatter) ?: stringResource(R.string.sleep_wake_label)
    val semanticDescription = when (summary) {
        LastSleepSummaryState.Empty -> stringResource(R.string.sleep_last_sleep_cd_empty)
        is LastSleepSummaryState.Populated ->
            stringResource(R.string.sleep_last_sleep_cd, summary.awakeForLabel, summary.endedAtLabel)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = semanticDescription },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            shape = MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sleep_last_sleep_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (summary) {
                        LastSleepSummaryState.Empty -> Text(
                            text = stringResource(R.string.sleep_last_sleep_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        is LastSleepSummaryState.Populated -> {
                            Text(
                                text = summary.awakeForLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = summary.endedAtLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                Surface(
                    onClick = onWakeTimeClick,
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .widthIn(max = 132.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (wakeTime != null) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = if (wakeTime != null) {
                                stringResource(R.string.sleep_wake_edit_cd)
                            } else {
                                stringResource(R.string.sleep_wake_set_cd)
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = wakeTimeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodayStatPill(
                    label = stringResource(R.string.sleep_stat_today),
                    value = if (totalSleep.isZero) "—" else totalSleep.formatDuration(),
                    modifier = Modifier.weight(1f),
                )
                TodayStatPill(
                    label = stringResource(R.string.sleep_stat_naps),
                    value = if (napCount == 0) "—" else napCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                TodayStatPill(
                    label = stringResource(R.string.sleep_stat_night),
                    value = if (nightSleep.isZero) "—" else nightSleep.formatDuration(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TodayStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableSleepEntry(
    record: SleepRecord,
    onDeleteRequest: (SleepRecord) -> Unit,
    onEditRecord: (SleepRecord) -> Unit,
    tinted: Boolean = false,
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
        SleepEntryCard(
            record = record,
            onEdit = { onEditRecord(record) },
            onDelete = { onDeleteRequest(record) },
            tinted = tinted,
        )
    }
}

@Composable
private fun SleepEntryDeleteBackground(targetValue: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        targetValue = if (targetValue == SwipeToDismissBoxValue.EndToStart) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            Color.Transparent
        },
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
            contentDescription = stringResource(R.string.sleep_cd_delete_entry),
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
    val sleepTypeLabel = stringResource(record.sleepType.labelRes())
    val deleteMessage = stringResource(R.string.sleep_delete_entry_message_text, sleepTypeLabel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_delete_entry_title)) },
        text = {
            Text(deleteMessage)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
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
        SleepIcon(modifier = Modifier.size(64.dp))
        Text(
            text = stringResource(R.string.sleep_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.sleep_empty_subtitle),
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
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NapIcon(modifier = Modifier.size(40.dp))
                Text(
                    text = stringResource(R.string.sleep_start_nap).replaceFirst(" ", "\n"),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Button(
            onClick = onStartNightSleep,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepIcon(modifier = Modifier.size(40.dp))
                Text(
                    text = stringResource(R.string.sleep_start_night).replaceFirst(" ", "\n"),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ActiveSleepCard(record: SleepRecord, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SleepInProgressTitle(record.sleepType)
            TimerDisplay(
                startTimeMillis = record.startTime.toEpochMilli(),
                isRunning = true,
                ringColor = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                elapsedFormatter = ::formatElapsedAsClock,
            )
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            ) {
                Text(stringResource(R.string.sleep_stop_session), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SleepInProgressTitle(sleepType: SleepType) {
    val label = stringResource(sleepType.labelRes())
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sleepType == SleepType.NIGHT_SLEEP) {
            SleepIcon(modifier = Modifier.size(18.dp))
        } else {
            NapIcon(modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(R.string.sleep_in_progress_status_text, label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

@Composable
internal fun SleepEntryCard(
    record: SleepRecord,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    tinted: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // Mirror the breastfeeding history: a soft, desaturated sleep-blue row tint with the text in the
    // section accent — deep blue in light, light blue in dark. Tracking-screen cards stay neutral.
    val isDark = LocalDarkTheme.current
    val rowText = if (isDark) Blue200 else Blue900
    val rowContainer = when {
        !tinted -> MaterialTheme.colorScheme.surface
        isDark -> Color(0xFF1E2A3A)
        else -> Color(0xFFE3F2FD)
    }
    val timeSubtitle = if (record.endTime != null) {
        stringResource(
            R.string.sleep_subtitle_range,
            record.startTime.formatTime12h(),
            record.endTime.formatTime12h(),
        )
    } else {
        record.startTime.formatTime12h()
    }
    // Mirror the partner feed badge (ADR-0003 op-inbox): attribute partner-started sessions on the primary app too.
    val subtitle = if (record.startedBy == SleepAuthor.PARTNER) {
        "$timeSubtitle · ${stringResource(R.string.sleep_author_partner_badge)}"
    } else {
        timeSubtitle
    }
    HistoryCard(
        title = stringResource(record.sleepType.labelRes()),
        subtitle = subtitle,
        trailing = record.endTime?.let { end ->
            Duration.between(record.startTime, end).formatDuration()
        } ?: stringResource(R.string.label_in_progress),
        badgeEmoji = "",
        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
        badgeContent = {
            if (record.sleepType == SleepType.NIGHT_SLEEP) {
                SleepIcon(modifier = Modifier.size(34.dp))
            } else {
                NapIcon(modifier = Modifier.size(34.dp))
            }
        },
        containerColor = rowContainer,
        titleColor = if (tinted) rowText else MaterialTheme.colorScheme.onSurface,
        subtitleColor = if (tinted) rowText.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
        trailingColor = if (tinted) rowText else MaterialTheme.colorScheme.secondary,
        trailingContent = if (onEdit != null && onDelete != null) {
            {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSleepEntrySheetContent(
    uiState: SleepUiState,
    onTypeChanged: (SleepType) -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onSave: () -> Unit,
    isEditing: Boolean = false,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a").withLocale(java.util.Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d").withLocale(java.util.Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val changeDateDescription = stringResource(R.string.change_date)
    val changeStartTimeDescription = stringResource(R.string.change_start_time)
    val changeEndTimeDescription = stringResource(R.string.change_end_time)

    if (showDatePicker) {
        val initialMillis = uiState.entryDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis ?: return@TextButton
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDateChanged(picked)
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isEditing) stringResource(R.string.sleep_entry_edit_title) else stringResource(R.string.sleep_entry_add_title),
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
                    label = {
                        val label = stringResource(type.labelRes())
                        Text(text = label)
                    },
                    leadingIcon = {
                        if (type == SleepType.NIGHT_SLEEP) {
                            SleepIcon(modifier = Modifier.size(18.dp))
                        } else {
                            NapIcon(modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.label_date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = changeDateDescription },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = uiState.entryDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_start_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(
                    onClick = onStartTimeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = changeStartTimeDescription },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = uiState.entryStartTime.format(timeFormatter),
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
                    text = stringResource(R.string.label_end_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(
                    onClick = onEndTimeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = changeEndTimeDescription },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = uiState.entryEndTime.format(timeFormatter),
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

        val canSave = uiState.entryDurationPreview != null && uiState.entryError == null

        when {
            uiState.entryError != null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.sleep_entry_error, uiState.entryError.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
            uiState.entryDurationPreview == null -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.sleep_entry_pick_times),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
            else -> {
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.sleep_entry_duration, uiState.entryDurationPreview.formatDuration()),
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
            isEditing && uiState.entryType == SleepType.NAP -> stringResource(R.string.sleep_update_nap)
            isEditing -> stringResource(R.string.sleep_update_night)
            uiState.entryType == SleepType.NAP -> stringResource(R.string.sleep_save_nap)
            else -> stringResource(R.string.sleep_save_night)
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
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
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = { TimePicker(state = timePickerState) }
    )
}
