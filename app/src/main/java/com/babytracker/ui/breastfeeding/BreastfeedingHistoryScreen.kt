package com.babytracker.ui.breastfeeding

import com.babytracker.ui.component.EmptyState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.ui.component.BreastfeedingIcon
import com.babytracker.ui.component.DeleteConfirmationDialog
import com.babytracker.ui.component.EditDeleteOverflowMenu
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.HistoryDayHeader
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.Pink200
import com.babytracker.ui.theme.Pink900
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
    val sortedGroups = remember(history) {
        history.groupByLocalDate { it.startTime }
            .entries
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
                title = { Text(stringResource(R.string.breastfeeding_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.breastfeeding_history_empty_title),
                subtitle = stringResource(R.string.breastfeeding_history_empty_subtitle),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                BreastfeedingIcon(modifier = Modifier.size(64.dp))
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
                    stickyHeader(key = date.toString()) {
                        val header = if (totalDuration.isZero) {
                            pluralStringResource(
                                R.plurals.breastfeeding_history_day_header,
                                sessions.size,
                                date.toRelativeLabel(
                                    stringResource(R.string.relative_today),
                                    stringResource(R.string.relative_yesterday),
                                ),
                                sessions.size,
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.breastfeeding_history_day_header_total,
                                sessions.size,
                                date.toRelativeLabel(
                                    stringResource(R.string.relative_today),
                                    stringResource(R.string.relative_yesterday),
                                ),
                                sessions.size,
                                totalDuration.formatDuration(),
                            )
                        }
                        HistoryDayHeader(label = header)
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
    // Mirror the diaper history row: a soft, desaturated pink tint rather than the vivid primaryContainer
    // fill, with the text in the section accent — deep pink in light, light pink in dark — for contrast.
    val isDark = LocalDarkTheme.current
    val rowContainer = if (isDark) Color(0xFF4A2A38) else Color(0xFFFCE4EC)
    val rowText = if (isDark) Pink200 else Pink900
    HistoryCard(
        title = if (isLeft) {
            stringResource(R.string.breastfeeding_side_left)
        } else {
            stringResource(R.string.breastfeeding_side_right)
        },
        subtitle = session.startTime.formatTime12h(),
        trailing = session.activeDuration?.formatDuration()
            ?: stringResource(R.string.breastfeeding_in_progress),
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        containerColor = rowContainer,
        titleColor = rowText,
        subtitleColor = rowText.copy(alpha = 0.7f),
        trailingColor = rowText,
        badgeContent = { BreastfeedingIcon(modifier = Modifier.size(34.dp)) },
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
    EditDeleteOverflowMenu(onEdit = onEdit, onDelete = onDelete)
}

@Composable
internal fun BreastfeedingDeleteConfirmationDialog(
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
