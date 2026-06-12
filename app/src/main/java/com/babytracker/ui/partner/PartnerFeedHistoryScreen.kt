package com.babytracker.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.ui.bottlefeed.BottleFeedSheet
import com.babytracker.ui.feeding.BottleFeedHistoryCard
import com.babytracker.ui.feeding.FeedingDeleteConfirmationDialog
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerFeedHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PartnerFeedHistoryViewModel = hiltViewModel(),
    bottleFeedViewModel: PartnerBottleFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bottleState by bottleFeedViewModel.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BottleFeedSnapshot?>(null) }

    LaunchedEffect(uiState.milkBags) {
        bottleFeedViewModel.onBagsAvailable(uiState.milkBags)
    }
    LaunchedEffect(uiState.accessRevoked) {
        if (uiState.accessRevoked) onNavigateBack()
    }
    LaunchedEffect(bottleState.saved) {
        if (bottleState.saved) {
            showSheet = false
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.partner_feeding_history)) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    bottleFeedViewModel.startLogging()
                    showSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.partner_log_bottle))
            }
        },
    ) { padding ->
        PartnerFeedHistoryContent(
            uiState = uiState,
            onEdit = { entry ->
                bottleFeedViewModel.startEditing(entry)
                showSheet = true
            },
            onDelete = { entry -> pendingDelete = entry },
            canEdit = viewModel::isEditable,
            onRetry = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    if (showSheet) {
        BottleFeedSheet(
            state = bottleState.copy(
                activeBags = if (bottleState.isEditing) emptyList() else bottleState.activeBags,
            ),
            onTypeChange = bottleFeedViewModel::onTypeChange,
            onVolumeChange = bottleFeedViewModel::onVolumeChange,
            onTimeChange = bottleFeedViewModel::onTimeChange,
            onBagSelect = bottleFeedViewModel::onBagSelect,
            onNotesChange = bottleFeedViewModel::onNotesChange,
            onConfirm = bottleFeedViewModel::onConfirm,
            onDismiss = { if (!bottleState.isSaving) showSheet = false },
        )
    }

    pendingDelete?.let { entry ->
        FeedingDeleteConfirmationDialog(
            onConfirm = {
                viewModel.onDelete(entry)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun PartnerFeedHistoryContent(
    uiState: PartnerFeedHistoryUiState,
    onEdit: (BottleFeedSnapshot) -> Unit,
    onDelete: (BottleFeedSnapshot) -> Unit,
    canEdit: (BottleFeedSnapshot) -> Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading && uiState.entries.isEmpty() -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null && uiState.entries.isEmpty() -> {
            Box(
                modifier = modifier.padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.partner_feeding_history_error_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.try_again))
                    }
                }
            }
        }
        uiState.entries.isEmpty() -> {
            Box(
                modifier = modifier.padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.feeding_history_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.feeding_history_empty_supporting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = uiState.entries,
                    key = { index, entry -> partnerFeedHistoryItemKey(index, entry) },
                ) { _, entry ->
                    val editable = canEdit(entry)
                    BottleFeedHistoryCard(
                        feed = entry.toDisplayFeed(),
                        volumeUnit = uiState.volumeUnit,
                        onEdit = { onEdit(entry) },
                        onDelete = { onDelete(entry) },
                        editable = editable,
                    )
                }
            }
        }
    }
}

private fun BottleFeedSnapshot.toDisplayFeed(): BottleFeed = BottleFeed(
    clientId = clientId,
    timestamp = Instant.ofEpochMilli(timestamp),
    volumeMl = volumeMl,
    type = FeedType.entries.firstOrNull { it.name == type } ?: FeedType.FORMULA,
    notes = notes,
    createdAt = Instant.EPOCH,
    author = if (author == FeedAuthor.PARTNER.name) FeedAuthor.PARTNER else FeedAuthor.OWNER,
)

internal fun partnerFeedHistoryItemKey(
    index: Int,
    entry: BottleFeedSnapshot,
): String = entry.clientId.ifEmpty { "legacy-$index" }
