package com.babytracker.ui.vaccine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.isOverdue
import com.babytracker.domain.model.isPastTarget
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.VaccinePalette
import com.babytracker.ui.theme.vaccineColors
import com.babytracker.util.toRelativeLabel
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun VaccineHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    historyViewModel: VaccineHistoryViewModel = hiltViewModel(),
    editViewModel: VaccineViewModel = hiltViewModel(),
) {
    val state by historyViewModel.uiState.collectAsStateWithLifecycle()
    val editState by editViewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by historyViewModel.pendingDelete.collectAsStateWithLifecycle()
    var showEditSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(editState.saved) { if (editState.saved) showEditSheet = false }

    val deletedMessage = stringResource(R.string.vaccine_deleted)
    val undoLabel = stringResource(R.string.vaccine_undo)
    LaunchedEffect(pendingDelete) {
        pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            // Deleting a medical record is high-stakes; give the undo window the longer duration.
            duration = SnackbarDuration.Long,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> historyViewModel.undoDelete()
            SnackbarResult.Dismissed -> historyViewModel.commitDelete()
        }
    }

    if (showEditSheet) {
        VaccineSheet(
            state = editState,
            onNameChange = editViewModel::onNameChange,
            onDoseChange = editViewModel::onDoseChange,
            onModeChange = editViewModel::onModeChange,
            onDateChange = editViewModel::onDateChange,
            onNotesChange = editViewModel::onNotesChange,
            onConfirm = editViewModel::onSave,
            onDismiss = { showEditSheet = false },
        )
    }

    VaccineHistoryContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onMarkGiven = historyViewModel::markGiven,
        onMarkScheduled = historyViewModel::markScheduled,
        onEditRecord = { record ->
            editViewModel.loadForEdit(record)
            showEditSheet = true
        },
        onDeleteRecord = historyViewModel::requestDelete,
        onRetry = historyViewModel::onRetry,
        onAddVaccine = {
            editViewModel.onStartAdd()
            showEditSheet = true
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineHistoryContent(
    state: VaccineHistoryUiState,
    snackbarHostState: SnackbarHostState,
    onMarkGiven: (Long) -> Unit,
    onMarkScheduled: (Long) -> Unit,
    onEditRecord: (VaccineRecord) -> Unit,
    onDeleteRecord: (VaccineRecord) -> Unit,
    onRetry: () -> Unit,
    onAddVaccine: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vaccine = vaccineColors()
    val overdueColor = if (LocalDarkTheme.current) OnWarningContainerAmberDark else OnWarningContainerAmber
    // Confirm before deleting: a vaccine record is a long-lived document (daycare, school), so it
    // must not vanish on a single half-asleep tap. The undo snackbar is the second safety net.
    var confirmDelete by remember { mutableStateOf<VaccineRecord?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vaccine_history_title)) },
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
        // History is where a parent goes to log a forgotten dose; give it the same add affordance as
        // the dashboard rather than forcing a back-navigation.
        bottomBar = { AddVaccineBar(colors = vaccine, onAddVaccine = onAddVaccine) },
    ) { padding ->
        when {
            state.isLoading -> HistorySkeleton(Modifier.padding(padding))
            state.isError -> HistoryError(colors = vaccine, onRetry = onRetry, modifier = Modifier.padding(padding))
            state.isEmpty -> HistoryEmpty(colors = vaccine, modifier = Modifier.padding(padding))
            else -> HistoryList(
                state = state,
                vaccine = vaccine,
                overdueColor = overdueColor,
                onMarkGiven = onMarkGiven,
                onMarkScheduled = onMarkScheduled,
                onEditRecord = onEditRecord,
                onRequestDelete = { confirmDelete = it },
                padding = padding,
            )
        }
    }

    confirmDelete?.let { record ->
        DeleteConfirmDialog(
            vaccine = vaccine,
            onConfirm = {
                onDeleteRecord(record)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun HistoryList(
    state: VaccineHistoryUiState,
    vaccine: VaccinePalette,
    overdueColor: Color,
    onMarkGiven: (Long) -> Unit,
    onMarkScheduled: (Long) -> Unit,
    onEditRecord: (VaccineRecord) -> Unit,
    onRequestDelete: (VaccineRecord) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp,
            start = 16.dp,
            end = 16.dp,
        ),
    ) {
        if (state.toSchedule.isNotEmpty()) {
            item(key = "to_schedule_header") {
                SectionHeader(stringResource(R.string.vaccine_to_schedule_section_title))
            }
            items(state.toSchedule, key = { "ts_${it.id}" }) { record ->
                ToScheduleRow(
                    record = record,
                    colors = vaccine,
                    isPastTarget = record.isPastTarget(state.now, ZoneId.systemDefault()),
                    onSchedule = { onMarkScheduled(record.id) },
                    onMarkGiven = { onMarkGiven(record.id) },
                    onDelete = { onRequestDelete(record) },
                    onEdit = { onEditRecord(record) },
                )
            }
        }
        if (state.upcoming.isNotEmpty()) {
            item(key = "upcoming_header") { SectionHeader(stringResource(R.string.vaccine_history_upcoming)) }
            items(state.upcoming, key = { "u_${it.id}" }) { record ->
                UpcomingRow(
                    record = record,
                    overdue = record.isOverdue(state.now, ZoneId.systemDefault()),
                    vaccine = vaccine,
                    overdueColor = overdueColor,
                    onMarkGiven = { onMarkGiven(record.id) },
                    onEdit = { onEditRecord(record) },
                    onDelete = { onRequestDelete(record) },
                )
            }
        }
        if (state.administeredByDate.isNotEmpty()) {
            item(key = "administered_header") {
                SectionHeader(stringResource(R.string.vaccine_history_administered))
            }
            state.administeredByDate.forEach { (date, records) ->
                item(key = "date_$date") { DateHeader(date) }
                items(records, key = { "a_${it.id}" }) { record ->
                    AdministeredRow(
                        record = record,
                        vaccine = vaccine,
                        onEdit = { onEditRecord(record) },
                        onDelete = { onRequestDelete(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(
        text = date.toRelativeLabel(
            stringResource(R.string.relative_today),
            stringResource(R.string.relative_yesterday),
        ).uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

/** Flat scheduled-vaccine row: tap the row to edit, the check to mark given, the trash to delete. */
@Composable
private fun UpcomingRow(
    record: VaccineRecord,
    overdue: Boolean,
    vaccine: VaccinePalette,
    overdueColor: Color,
    onMarkGiven: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editLabel = stringResource(R.string.vaccine_edit_content_description, record.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (overdue) overdueColor else vaccine.accent, filled = false)
        Spacer(Modifier.size(12.dp))
        val date = record.scheduledDate?.toVaccineDateLabel().orEmpty()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.nameWithDose(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (overdue) {
                    stringResource(R.string.vaccine_overdue_on_date, date)
                } else {
                    stringResource(R.string.vaccine_scheduled_for, date)
                },
                style = MaterialTheme.typography.bodySmall,
                // Color carries the urgency; the text stays factual.
                color = if (overdue) overdueColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMarkGiven) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.vaccine_mark_given_content_description, record.name),
                tint = vaccine.accent,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.vaccine_delete_content_description, record.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Flat administered row under a day header: tap to edit, trash to delete. */
@Composable
private fun AdministeredRow(
    record: VaccineRecord,
    vaccine: VaccinePalette,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editLabel = stringResource(R.string.vaccine_edit_content_description, record.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = vaccine.accent, filled = true)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.nameWithDose(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            record.notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.vaccine_delete_content_description, record.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryEmpty(colors: VaccinePalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Vaccines,
            contentDescription = null,
            tint = colors.accent.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.vaccine_history_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryError(colors: VaccinePalette, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val announce = stringResource(R.string.vaccine_load_error)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            // Announce the failure on transition, not only when a screen reader lands on the text.
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announce
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.vaccine_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.try_again), color = colors.accent)
        }
    }
}

/** Quiet placeholder until the first Room emission lands, mirroring the dashboard's calm skeleton. */
@Composable
private fun HistorySkeleton(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val loadingLabel = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Spacer(Modifier.height(16.dp))
        repeat(5) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index % 3 == 0) 0.4f else 1f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(block),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

