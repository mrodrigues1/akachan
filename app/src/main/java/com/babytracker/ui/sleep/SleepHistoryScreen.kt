package com.babytracker.ui.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.util.formatDuration
import com.babytracker.util.toRelativeLabel
import java.time.Duration
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groupedByDateDesc by viewModel.historyByDateDesc.collectAsStateWithLifecycle()

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
                title = { Text("Sleep History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (groupedByDateDesc.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🌙", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No sleep records yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sleep sessions you track will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Back to Sleep", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
                groupedByDateDesc.forEach { (date, records) ->
                    val totalDuration = records
                        .filter { it.endTime != null }
                        .mapNotNull { record ->
                            record.endTime?.let { end -> Duration.between(record.startTime, end) }
                        }
                        .fold(Duration.ZERO) { acc, d -> acc + d }
                    val totalLabel = if (totalDuration.isZero) "" else " · ${totalDuration.formatDuration()} total"

                    stickyHeader(key = date.toString()) {
                        Text(
                            text = "${date.toRelativeLabel()} · ${records.size} records$totalLabel".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    items(records, key = { it.id }) { record ->
                        if (record.endTime != null) {
                            SwipeableSleepEntry(
                                record = record,
                                onDeleteRequest = viewModel::onDeleteRequest,
                                onEditRecord = viewModel::onEditRecord
                            )
                        } else {
                            SleepEntryCard(record = record)
                        }
                    }
                }
            }
        }
    }
}
