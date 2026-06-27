package com.babytracker.ui.diaper

import com.babytracker.ui.component.EmptyState

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.DiaperChange
import com.babytracker.ui.component.DiaperIcon
import com.babytracker.ui.component.DiaperTypeIcon
import com.babytracker.ui.component.HistoryDayHeader
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.diaperColors
import com.babytracker.util.toRelativeLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    historyViewModel: DiaperHistoryViewModel = hiltViewModel(),
    editViewModel: DiaperViewModel = hiltViewModel(),
) {
    val grouped by historyViewModel.historyByDateDesc.collectAsStateWithLifecycle()
    val editState by editViewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by historyViewModel.pendingDelete.collectAsStateWithLifecycle()
    var showEditSheet by remember { mutableStateOf(false) }

    LaunchedEffect(editState.saved) {
        if (editState.saved) showEditSheet = false
    }

    if (showEditSheet) {
        DiaperSheet(
            state = editState,
            onTypeChange = editViewModel::onTypeChange,
            onTimeChange = editViewModel::onTimeChange,
            onNotesChange = editViewModel::onNotesChange,
            onConfirm = editViewModel::onSave,
            onDismiss = { showEditSheet = false },
        )
    }

    if (pendingDelete != null) {
        DiaperDeleteConfirmationDialog(
            onConfirm = historyViewModel::onConfirmDelete,
            onDismiss = historyViewModel::onCancelDelete,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diaper_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (grouped.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.diaper_history_empty),
                subtitle = stringResource(R.string.diaper_history_empty_supporting),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                DiaperIcon(modifier = Modifier.size(64.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            ) {
                grouped.forEach { (date, changes) ->
                    stickyHeader(key = date.toString()) {
                        HistoryDayHeader(
                            label = pluralStringResource(
                                R.plurals.diaper_history_day_header,
                                changes.size,
                                date.toRelativeLabel(
                                    stringResource(R.string.relative_today),
                                    stringResource(R.string.relative_yesterday),
                                ),
                                changes.size,
                            ),
                            modifier = Modifier.background(MaterialTheme.colorScheme.background),
                            color = diaperColors().onContainer,
                        )
                    }
                    items(changes, key = { it.id }) { change ->
                        DiaperHistoryRow(
                            change = change,
                            onEdit = {
                                editViewModel.loadForEdit(
                                    id = change.id,
                                    timestamp = change.timestamp,
                                    type = change.type,
                                    notes = change.notes,
                                    createdAt = change.createdAt,
                                )
                                showEditSheet = true
                            },
                            onDelete = { historyViewModel.onDeleteRequest(change) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaperHistoryRow(
    change: DiaperChange,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val time = timeFormatter.format(change.timestamp.atZone(ZoneId.systemDefault()).toLocalTime())
    val isDark = LocalDarkTheme.current
    val diaper = diaperColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = diaper.container),
        // HistoryCard rule: 1dp ambient lift in light; swap to an onContainer stroke in dark.
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
        border = if (isDark) BorderStroke(1.dp, diaper.onContainer.copy(alpha = 0.2f)) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section-colored square tile behind the icon, mirroring the breastfeeding history badge.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(diaper.onContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                DiaperTypeIcon(
                    type = change.type,
                    modifier = Modifier.size(34.dp),
                )
            }
            val typeLabel = stringResource(change.type.labelRes())
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.diaper_row_summary, typeLabel, time),
                    style = MaterialTheme.typography.titleSmall,
                    color = diaper.onContainer,
                )
                change.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = diaper.onContainer.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.diaper_edit_content_description, typeLabel, time),
                    tint = diaper.onContainer,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.diaper_delete_content_description, typeLabel, time),
                    tint = diaper.onContainer,
                )
            }
        }
    }
}

@Composable
private fun DiaperDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val diaper = diaperColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diaper_delete_title)) },
        text = { Text(stringResource(R.string.diaper_delete_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                // Destructive action carries the error role, not the friendly domain accent.
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = diaper.onContainer),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}
