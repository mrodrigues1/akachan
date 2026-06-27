package com.babytracker.ui.pumping

import com.babytracker.ui.component.EmptyState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.PumpingSession
import com.babytracker.ui.component.DeleteConfirmationDialog
import com.babytracker.ui.component.EditDeleteOverflowMenu
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.HistoryDayHeader
import com.babytracker.ui.component.PumpingIcon
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.Pink200
import com.babytracker.ui.theme.Pink900
import com.babytracker.util.formatDuration
import com.babytracker.util.formatVolume
import com.babytracker.util.formatTime12h
import com.babytracker.util.groupByLocalDate
import com.babytracker.util.toRelativeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpingHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PumpingHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pumping_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PumpingHistoryContent(
            state = state,
            onEditClicked = viewModel::onEditClicked,
            onDeleteClicked = viewModel::onPendingDeleteSessionChanged,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        )
    }

    if (state.pendingDeleteSession != null) {
        PumpingDeleteConfirmationDialog(
            onConfirm = viewModel::onConfirmDeleteSession,
            onDismiss = { viewModel.onPendingDeleteSessionChanged(null) },
        )
    }

    state.editSheet?.let { sheet ->
        EditPumpingSessionSheet(
            state = sheet,
            onFieldChange = viewModel::onEditFieldChange,
            onDismiss = viewModel::onEditDismiss,
            onSave = viewModel::onEditSave,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onDeleteCancelled = viewModel::onDeleteCancelled,
        )
    }
}

@Composable
internal fun PumpingHistoryContent(
    state: PumpingHistoryUiState,
    onEditClicked: (PumpingSession) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteClicked: (PumpingSession) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    // Mirror the breastfeeding history: a soft, desaturated pink row tint with the text in the section
    // accent — deep pink in light, light pink in dark — for contrast.
    val isDark = LocalDarkTheme.current
    val rowText = if (isDark) Pink200 else Pink900
    val rowContainer = if (isDark) Color(0xFF4A2A38) else Color(0xFFFCE4EC)
    Box(modifier = modifier.fillMaxSize()) {
        if (state.sessions.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.pumping_history_empty_title),
                subtitle = stringResource(R.string.breastfeeding_history_empty_subtitle),
                modifier = Modifier.fillMaxSize(),
            ) {
                PumpingIcon(modifier = Modifier.size(64.dp))
            }
            return@Box
        }

        val sortedGroups = remember(state.sessions) {
            state.sessions.groupByLocalDate { it.startTime }
                .entries.sortedByDescending { it.key }.map { (date, sessions) -> date to sessions }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            sortedGroups.forEach { (date, sessions) ->
                stickyHeader(key = date.toString()) {
                    HistoryDayHeader(
                        label = pluralStringResource(
                            R.plurals.pumping_history_day_header,
                            sessions.size,
                            date.toRelativeLabel(
                                stringResource(R.string.relative_today),
                                stringResource(R.string.relative_yesterday),
                            ),
                            sessions.size,
                        ),
                    )
                }

                items(sessions, key = { it.id }) { session ->
                    val volumeLabel = session.volumeMl?.let { formatVolume(it, state.volumeUnit) } ?: "—"
                    HistoryCard(
                        title = stringResource(R.string.pumping_history_item, stringResource(session.breast.labelRes()), volumeLabel),
                        subtitle = session.startTime.formatTime12h(),
                        trailing = session.activeDuration?.formatDuration() ?: stringResource(R.string.label_in_progress),
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        badgeContent = { PumpingIcon(modifier = Modifier.size(34.dp)) },
                        containerColor = rowContainer,
                        titleColor = rowText,
                        subtitleColor = rowText.copy(alpha = 0.7f),
                        trailingColor = rowText,
                        onClick = { onEditClicked(session) },
                        trailingContent = {
                            PumpingSessionOverflowMenu(
                                onEdit = { onEditClicked(session) },
                                onDelete = { onDeleteClicked(session) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Three-dots overflow menu (Edit / Delete) on a pumping history row, mirroring the feed history menu. */
@Composable
private fun PumpingSessionOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    EditDeleteOverflowMenu(onEdit = onEdit, onDelete = onDelete)
}

@Composable
private fun PumpingDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DeleteConfirmationDialog(
        title = stringResource(R.string.breastfeeding_delete_title),
        message = stringResource(R.string.breastfeeding_delete_message),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
