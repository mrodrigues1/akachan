package com.babytracker.ui.vaccine

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.isOverdue
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.ui.theme.vaccineColors
import com.babytracker.util.toRelativeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

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
            duration = SnackbarDuration.Short,
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
        onEditRecord = { record ->
            editViewModel.loadForEdit(record)
            showEditSheet = true
        },
        onDeleteRecord = historyViewModel::requestDelete,
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
    onEditRecord: (VaccineRecord) -> Unit,
    onDeleteRecord: (VaccineRecord) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    ) { padding ->
        if (state.isEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("💉", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.vaccine_history_empty),
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
                if (state.upcoming.isNotEmpty()) {
                    item(key = "upcoming_header") { SectionHeader(stringResource(R.string.vaccine_history_upcoming)) }
                    items(state.upcoming, key = { "u_${it.id}" }) { record ->
                        VaccineUpcomingRow(
                            record = record,
                            overdue = record.isOverdue(state.now),
                            onMarkGiven = { onMarkGiven(record.id) },
                            onEdit = { onEditRecord(record) },
                            onDelete = { onDeleteRecord(record) },
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
                            VaccineAdministeredRow(
                                record = record,
                                onEdit = { onEditRecord(record) },
                                onDelete = { onDeleteRecord(record) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = vaccineColors().onContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(
        text = date.toRelativeLabel(
            stringResource(R.string.relative_today),
            stringResource(R.string.relative_yesterday),
        ).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun VaccineUpcomingRow(
    record: VaccineRecord,
    overdue: Boolean,
    onMarkGiven: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val vaccine = vaccineColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = vaccine.container),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.nameWithDose(),
                        style = MaterialTheme.typography.titleSmall,
                        color = vaccine.onContainer,
                    )
                    record.scheduledDate?.let {
                        Text(
                            text = stringResource(R.string.vaccine_scheduled_for, it.toDateLabel()),
                            style = MaterialTheme.typography.bodySmall,
                            color = vaccine.onContainer,
                        )
                    }
                    if (overdue) {
                        Spacer(Modifier.height(4.dp))
                        OverdueBadge()
                    }
                }
                RowActions(record, onEdit, onDelete)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onMarkGiven,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaccine.accent,
                    contentColor = vaccine.onAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vaccine_mark_given), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun VaccineAdministeredRow(
    record: VaccineRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.nameWithDose(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                record.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RowActions(record, onEdit, onDelete)
        }
    }
}

@Composable
private fun RowActions(record: VaccineRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.vaccine_edit_content_description, record.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun OverdueBadge() {
    val isDark = LocalDarkTheme.current
    Text(
        text = stringResource(R.string.vaccine_overdue_badge),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber,
        modifier = Modifier
            .background(
                color = if (isDark) WarningContainerAmberDark else WarningContainerAmber,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun VaccineRecord.nameWithDose(): String =
    doseLabel?.takeIf { it.isNotBlank() }?.let { "$name · $it" } ?: name

private fun Instant.toDateLabel(): String =
    dateFormatter.format(atZone(ZoneId.systemDefault()).toLocalDate())
