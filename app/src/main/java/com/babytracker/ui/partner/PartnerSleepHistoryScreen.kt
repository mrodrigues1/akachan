package com.babytracker.ui.partner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.toSleepTypeOrNull
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.ui.component.HistoryCard
import com.babytracker.ui.component.NapIcon
import com.babytracker.ui.component.SleepIcon
import com.babytracker.ui.sleep.labelRes
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import com.babytracker.util.toRelativeLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSleepHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PartnerSleepHistoryViewModel = hiltViewModel(),
    sleepViewModel: PartnerSleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sleepState by sleepViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.accessRevoked) {
        if (uiState.accessRevoked) onNavigateBack()
    }
    LaunchedEffect(sleepState.accessRevoked) {
        if (sleepState.accessRevoked) {
            sleepViewModel.onAccessRevokedHandled()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.partner_sleep_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PartnerSleepHistoryContent(
            uiState = uiState,
            canEdit = viewModel::isEditable,
            onEdit = sleepViewModel::startEditing,
            onRetry = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    sleepState.editor?.let { editor ->
        PartnerSleepEditorSheet(
            editor = editor,
            onTypeChange = sleepViewModel::onEditorTypeChange,
            onStartChange = sleepViewModel::onEditorStartChange,
            onEndChange = sleepViewModel::onEditorEndChange,
            onNotesChange = sleepViewModel::onEditorNotesChange,
            onConfirm = sleepViewModel::onConfirmEdit,
            onDismiss = sleepViewModel::onDismissEditor,
        )
    }
}

@Composable
private fun PartnerSleepHistoryContent(
    uiState: PartnerSleepHistoryUiState,
    canEdit: (SleepSnapshot) -> Boolean,
    onEdit: (SleepSnapshot) -> Unit,
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
            CenteredMessage(modifier = modifier) {
                Text(stringResource(R.string.sleep_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
            }
        }
        uiState.entries.isEmpty() -> {
            CenteredMessage(modifier = modifier) {
                Text(stringResource(R.string.sleep_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.partner_sleep_history_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            SleepHistoryList(uiState.entries, canEdit, onEdit, modifier)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleepHistoryList(
    entries: List<SleepSnapshot>,
    canEdit: (SleepSnapshot) -> Boolean,
    onEdit: (SleepSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val grouped = remember(entries) {
        entries
            .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
    }
    val today = stringResource(R.string.relative_today)
    val yesterday = stringResource(R.string.relative_yesterday)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        grouped.forEach { (date, dayRecords) ->
            stickyHeader(key = date.toString()) {
                Text(
                    text = date.toRelativeLabel(today, yesterday).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
            items(dayRecords, key = { it.clientId.ifEmpty { "id-${it.id}" } }) { record ->
                PartnerSleepHistoryCard(
                    record = record,
                    editable = canEdit(record),
                    onEdit = { onEdit(record) },
                )
            }
        }
    }
}

@Composable
private fun PartnerSleepHistoryCard(
    record: SleepSnapshot,
    editable: Boolean,
    onEdit: () -> Unit,
) {
    val type = record.sleepType.toSleepTypeOrNull() ?: SleepType.NAP
    val start = Instant.ofEpochMilli(record.startTime)
    val end = record.endTime?.let(Instant::ofEpochMilli)
    val timeSubtitle = if (end != null) {
        stringResource(R.string.sleep_subtitle_range, start.formatTime12h(), end.formatTime12h())
    } else {
        start.formatTime12h()
    }
    val subtitle = if (record.startedBy == com.babytracker.domain.model.SleepAuthor.PARTNER.name) {
        "$timeSubtitle · ${stringResource(R.string.sleep_author_partner_badge)}"
    } else {
        timeSubtitle
    }
    HistoryCard(
        title = stringResource(type.labelRes()),
        subtitle = subtitle,
        trailing = end?.let { Duration.between(start, it).formatDuration() }
            ?: stringResource(R.string.label_in_progress),
        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
        badgeContent = {
            if (type == SleepType.NIGHT_SLEEP) {
                SleepIcon(Modifier.size(34.dp))
            } else {
                NapIcon(Modifier.size(34.dp))
            }
        },
        trailingColor = MaterialTheme.colorScheme.secondary,
        trailingContent = if (editable) {
            {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.partner_edit_sleep_cd),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CenteredMessage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}
