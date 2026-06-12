package com.babytracker.ui.feeding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.FeedingDayGroup
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.ui.breastfeeding.BreastfeedingDeleteConfirmationDialog
import com.babytracker.ui.breastfeeding.BreastfeedingViewModel
import com.babytracker.ui.breastfeeding.EditBreastfeedingSessionSheet
import com.babytracker.ui.breastfeeding.FeedSessionOverflowMenu
import com.babytracker.ui.bottlefeed.BottleFeedSheet
import com.babytracker.ui.bottlefeed.BottleFeedViewModel
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import com.babytracker.util.formatVolume
import com.babytracker.util.toRelativeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedFeedingHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedingHistoryViewModel = hiltViewModel(),
    bottleViewModel: BottleFeedViewModel = hiltViewModel(),
    breastfeedingViewModel: BreastfeedingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bottleState by bottleViewModel.uiState.collectAsStateWithLifecycle()
    val breastfeedingState by breastfeedingViewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BottleFeed?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bottleState.saved) {
        if (bottleState.saved) editing = false
    }

    LaunchedEffect(state.deletedBottleId) {
        val deletedId = state.deletedBottleId ?: return@LaunchedEffect
        if (pendingDelete?.id == deletedId) pendingDelete = null
        viewModel.onDeleteResultConsumed()
    }

    LaunchedEffect(state.deleteError) {
        val message = state.deleteError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onDeleteErrorShown()
    }

    LaunchedEffect(breastfeedingState.error) {
        val message = breastfeedingState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        breastfeedingViewModel.onErrorDismissed()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeding_history_title)) },
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
    ) { padding ->
        UnifiedFeedingHistoryContent(
            state = state,
            onEditBottle = { feed ->
                bottleViewModel.loadForEdit(
                    id = feed.id,
                    timestamp = feed.timestamp,
                    volumeMl = feed.volumeMl,
                    type = feed.type,
                    linkedMilkBagId = feed.linkedMilkBagId,
                    notes = feed.notes,
                )
                editing = true
            },
            onDeleteBottle = { pendingDelete = it },
            onEditBreastfeeding = breastfeedingViewModel::onEditSessionClick,
            onDeleteBreastfeeding = breastfeedingViewModel::onPendingDeleteSessionChanged,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        )
    }

    if (editing) {
        BottleFeedSheet(
            state = bottleState,
            onTypeChange = bottleViewModel::onTypeChange,
            onVolumeChange = bottleViewModel::onVolumeChange,
            onTimeChange = bottleViewModel::onTimeChange,
            onBagSelect = bottleViewModel::onBagSelect,
            onNotesChange = bottleViewModel::onNotesChange,
            onConfirm = bottleViewModel::onSave,
            onDismiss = { editing = false },
        )
    }

    pendingDelete?.let { feed ->
        FeedingDeleteConfirmationDialog(
            onConfirm = {
                viewModel.onDeleteBottle(feed)
            },
            onDismiss = { if (!state.isDeleting) pendingDelete = null },
            confirmEnabled = !state.isDeleting,
        )
    }

    val editSheet = breastfeedingState.editSheet
    if (editSheet != null) {
        EditBreastfeedingSessionSheet(
            state = editSheet,
            onStartChanged = { breastfeedingViewModel.onEditTimeChanged(it, editSheet.editedEnd) },
            onEndChanged = { breastfeedingViewModel.onEditTimeChanged(editSheet.editedStart, it) },
            onDismiss = breastfeedingViewModel::onEditDismiss,
            onSave = breastfeedingViewModel::onEditSave,
        )
    }

    if (breastfeedingState.pendingDeleteSession != null) {
        BreastfeedingDeleteConfirmationDialog(
            onConfirm = breastfeedingViewModel::onConfirmDeleteSession,
            onDismiss = { breastfeedingViewModel.onPendingDeleteSessionChanged(null) },
        )
    }
}

@Composable
internal fun UnifiedFeedingHistoryContent(
    state: FeedingHistoryUiState,
    onEditBottle: (BottleFeed) -> Unit,
    onDeleteBottle: (BottleFeed) -> Unit,
    onEditBreastfeeding: (BreastfeedingSession) -> Unit,
    onDeleteBreastfeeding: (BreastfeedingSession) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingHistoryState()
            state.days.isEmpty() -> EmptyFeedingHistoryState()
            else -> FeedingHistoryList(
                days = state.days,
                volumeUnit = state.volumeUnit,
                onEditBottle = onEditBottle,
                onDeleteBottle = onDeleteBottle,
                onEditBreastfeeding = onEditBreastfeeding,
                onDeleteBreastfeeding = onDeleteBreastfeeding,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun FeedingHistoryList(
    days: List<FeedingDayGroup>,
    volumeUnit: VolumeUnit,
    onEditBottle: (BottleFeed) -> Unit,
    onDeleteBottle: (BottleFeed) -> Unit,
    onEditBreastfeeding: (BreastfeedingSession) -> Unit,
    onDeleteBreastfeeding: (BreastfeedingSession) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        days.forEach { day ->
            stickyHeader(key = day.date.toString()) {
                DayHeader(day = day, volumeUnit = volumeUnit)
            }
            items(day.entries, key = { entry -> entry.historyKey() }) { entry ->
                when (entry) {
                    is FeedEntry.Bottle -> BottleFeedHistoryCard(
                        feed = entry.feed,
                        volumeUnit = volumeUnit,
                        onEdit = { onEditBottle(entry.feed) },
                        onDelete = { onDeleteBottle(entry.feed) },
                    )

                    is FeedEntry.Breastfeeding -> BreastfeedingFeedHistoryCard(
                        session = entry.session,
                        onEdit = { onEditBreastfeeding(entry.session) },
                        onDelete = { onDeleteBreastfeeding(entry.session) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: FeedingDayGroup,
    volumeUnit: VolumeUnit,
) {
    val totalVolume = formatVolume(day.totals.bottleVolumeMl, volumeUnit)
    Text(
        text = stringResource(
            R.string.feeding_history_day_totals,
            "${day.date.toRelativeLabel()} · $totalVolume",
            day.totals.totalFeedCount,
        ).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
internal fun BottleFeedHistoryCard(
    feed: BottleFeed,
    volumeUnit: VolumeUnit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    editable: Boolean = true,
) {
    HistoryCard(
        title = feed.type.historyLabel(),
        subtitle = buildString {
            append(feed.timestamp.formatTime12h())
            if (feed.linkedMilkBagId != null) append(" · from stash")
            if (feed.author == FeedAuthor.PARTNER) {
                append(" · ").append(stringResource(R.string.feed_author_partner_badge))
            }
        },
        trailing = formatVolume(feed.volumeMl, volumeUnit),
        badgeEmoji = "🍼",
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        onClick = if (editable) onEdit else null,
        trailingContent = if (editable) {
            { BottleFeedOverflowMenu(onEdit = onEdit, onDelete = onDelete) }
        } else {
            null
        },
    )
}

@Composable
internal fun BreastfeedingFeedHistoryCard(
    session: BreastfeedingSession,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sideLabel = if (session.startingSide == BreastSide.LEFT) "Left side" else "Right side"
    HistoryCard(
        title = sideLabel,
        subtitle = session.startTime.formatTime12h(),
        trailing = session.activeDuration?.formatDuration() ?: "In progress",
        badgeEmoji = "🤱",
        badgeColor = MaterialTheme.colorScheme.primaryContainer,
        onClick = onEdit,
        trailingContent = { FeedSessionOverflowMenu(onEdit = onEdit, onDelete = onDelete) },
    )
}

@Composable
private fun RowScope.BottleFeedOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
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

@Composable
internal fun FeedingDeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeding_delete_title)) },
        text = { Text(stringResource(R.string.feeding_delete_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun EmptyFeedingHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🍼", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.feeding_history_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.feeding_history_empty_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LoadingHistoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun FeedType.historyLabel(): String = when (this) {
    FeedType.BREAST_MILK -> "Breast milk bottle"
    FeedType.FORMULA -> "Formula bottle"
}

private fun FeedEntry.historyKey(): String = when (this) {
    is FeedEntry.Bottle -> "bottle-${feed.id}-${feed.timestamp.toEpochMilli()}"
    is FeedEntry.Breastfeeding -> "breastfeeding-${session.id}-${session.startTime.toEpochMilli()}"
}
