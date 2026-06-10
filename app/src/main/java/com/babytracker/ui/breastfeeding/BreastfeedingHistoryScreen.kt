package com.babytracker.ui.breastfeeding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import com.babytracker.util.groupByLocalDate
import com.babytracker.util.toRelativeLabel
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreastfeedingHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BreastfeedingViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val grouped = history.groupByLocalDate { it.startTime }
    val sortedGroups = remember(grouped) {
        grouped.entries
            .sortedByDescending { it.key }
            .map { (date, sessions) ->
                val totalDuration = sessions
                    .mapNotNull { it.activeDuration }
                    .fold(Duration.ZERO) { acc, d -> acc + d }
                Triple(date, sessions, totalDuration)
            }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Feeding History") },
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
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🍼", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No sessions yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Sessions you track will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
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
                sortedGroups.forEach { (date, sessions, totalDuration) ->
                    val totalLabel = if (totalDuration.isZero) "" else " · ${totalDuration.formatDuration()} total"

                    stickyHeader(key = date.toString()) {
                        Text(
                            text = "${date.toRelativeLabel()} · ${sessions.size} sessions$totalLabel".uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    items(sessions, key = { it.id }) { session ->
                        FeedHistoryCard(
                            session = session,
                            onEdit = { viewModel.onEditSessionClick(session) },
                            onDelete = { viewModel.onPendingDeleteSessionChanged(session) },
                        )
                    }
                }
            }
        }
    }

    val editSheet = uiState.editSheet
    if (editSheet != null) {
        EditBreastfeedingSessionSheet(
            state = editSheet,
            onStartChanged = { viewModel.onEditTimeChanged(it, editSheet.editedEnd) },
            onEndChanged = { viewModel.onEditTimeChanged(editSheet.editedStart, it) },
            onDismiss = viewModel::onEditDismiss,
            onSave = viewModel::onEditSave,
        )
    }

    if (uiState.pendingDeleteSession != null) {
        BreastfeedingDeleteConfirmationDialog(
            onConfirm = viewModel::onConfirmDeleteSession,
            onDismiss = { viewModel.onPendingDeleteSessionChanged(null) },
        )
    }
}

@Composable
internal fun FeedHistoryCard(
    session: BreastfeedingSession,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isLeft = session.startingSide == BreastSide.LEFT
    HistoryCard(
        title = if (isLeft) "Left side" else "Right side",
        subtitle = session.startTime.formatTime12h(),
        trailing = session.activeDuration?.formatDuration() ?: "In progress",
        badgeEmoji = "🍼",
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        onClick = onEdit,
        trailingContent = { FeedSessionOverflowMenu(onEdit = onEdit, onDelete = onDelete) },
    )
}

/**
 * Three-dots overflow menu (Edit / Delete) shared by the feed history cards and the
 * last-feeding card on the main screen, mirroring the sleep card menu.
 */
@Composable
internal fun FeedSessionOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
internal fun BreastfeedingDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this session?") },
        text = { Text("It can't be undone.") },
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
