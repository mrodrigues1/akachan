package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.SleepType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackingScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Tracking") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Track Sleep",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Log a sleep entry with start and end times",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::onAddEntryClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Add Sleep Entry", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

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

        if (uiState.showEntrySheet) {
            ModalBottomSheet(
                onDismissRequest = viewModel::onDismissSheet,
                sheetState = sheetState
            ) {
                SleepEntrySheet(
                    uiState = uiState,
                    onTypeChanged = viewModel::onEntryTypeChanged,
                    onStartTimeChanged = viewModel::onEntryStartTimeChanged,
                    onEndTimeChanged = viewModel::onEntryEndTimeChanged,
                    onSave = viewModel::onSaveEntry,
                    onDismiss = viewModel::onDismissSheet
                )
            }
        }
    }
}

@Composable
private fun SleepEntrySheet(
    uiState: SleepUiState,
    onTypeChanged: (SleepType) -> Unit,
    onStartTimeChanged: (java.time.LocalTime) -> Unit,
    onEndTimeChanged: (java.time.LocalTime) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Log Sleep",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Sleep type selector
        Text(
            text = "Sleep type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SleepType.entries.forEach { type ->
                val isSelected = uiState.entryType == type
                if (isSelected) {
                    Button(
                        onClick = { onTypeChanged(type) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("${type.emoji} ${type.label}", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onTypeChanged(type) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("${type.emoji} ${type.label}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Time pickers
        TimePickerRow(
            label = "Start time",
            time = uiState.entryStartTime,
            onTimeChanged = onStartTimeChanged
        )

        TimePickerRow(
            label = "End time",
            time = uiState.entryEndTime,
            onTimeChanged = onEndTimeChanged
        )

        // Error message
        if (uiState.entryError != null) {
            Text(
                text = uiState.entryError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun TimePickerRow(
    label: String,
    time: java.time.LocalTime,
    onTimeChanged: (java.time.LocalTime) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = {
                onTimeChanged(time.minusMinutes(15))
            }) { Text("-15m") }

            Text(
                text = time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = {
                onTimeChanged(time.plusMinutes(15))
            }) { Text("+15m") }
        }
    }
}
