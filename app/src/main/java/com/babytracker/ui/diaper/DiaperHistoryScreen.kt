package com.babytracker.ui.diaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.DiaperChange
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditSheet by remember { mutableStateOf(false) }

    LaunchedEffect(editState.saved) {
        if (editState.saved) showEditSheet = false
    }
    LaunchedEffect(Unit) {
        historyViewModel.deletions.collect { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = "Diaper change deleted",
                actionLabel = "Undo",
            )
            if (result == SnackbarResult.ActionPerformed) historyViewModel.onUndoDelete(deleted)
        }
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Diaper History") },
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
        if (grouped.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🧷", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No diaper changes yet",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
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
                        Text(
                            text = "${date.toRelativeLabel()} · ${changes.size} changes".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                            onDelete = { historyViewModel.onDelete(change) },
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
    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(change.type.emoji, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${change.type.label} · $time",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                change.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete ${change.type.label} change at $time",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
