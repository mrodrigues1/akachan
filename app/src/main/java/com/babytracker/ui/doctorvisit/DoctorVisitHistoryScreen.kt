package com.babytracker.ui.doctorvisit

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.hasSnapshot
import com.babytracker.ui.theme.doctorVisitColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Localized full date incl. weekday, locale-aware field order (e.g. "Saturday, June 20, 2026").
private val visitDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

@Composable
fun DoctorVisitHistoryScreen(
    onAddOrEdit: (Long?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorVisitHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deletedMessage = stringResource(R.string.doctor_visit_history_deleted)
    val undoLabel = stringResource(R.string.doctor_visit_history_undo)
    LaunchedEffect(state.lastDeleted) {
        state.lastDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.onUndoDelete()
            SnackbarResult.Dismissed -> viewModel.onUndoConsumed()
        }
    }

    DoctorVisitHistoryContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAdd = { onAddOrEdit(null) },
        onEdit = { onAddOrEdit(it.id) },
        onDelete = viewModel::onDelete,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorVisitHistoryContent(
    state: DoctorVisitHistoryUiState,
    snackbarHostState: SnackbarHostState,
    onAdd: () -> Unit,
    onEdit: (DoctorVisit) -> Unit,
    onDelete: (DoctorVisit) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = doctorVisitColors()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doctor_visit_history_title)) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.doctor_visit_add))
            }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyHistory(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            ) {
                if (state.upcoming.isNotEmpty()) {
                    item(key = "upcoming_header") {
                        SectionHeader(stringResource(R.string.doctor_visit_history_upcoming))
                    }
                    items(state.upcoming, key = { "u_${it.id}" }) { visit ->
                        VisitRow(visit, state.questionCounts[visit.id] ?: 0, onEdit, onDelete)
                    }
                }
                if (state.past.isNotEmpty()) {
                    item(key = "past_header") {
                        SectionHeader(stringResource(R.string.doctor_visit_history_past))
                    }
                    items(state.past, key = { "p_${it.id}" }) { visit ->
                        VisitRow(visit, state.questionCounts[visit.id] ?: 0, onEdit, onDelete)
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
        color = doctorVisitColors().onContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitRow(
    visit: DoctorVisit,
    questionCount: Int,
    onEdit: (DoctorVisit) -> Unit,
    onDelete: (DoctorVisit) -> Unit,
) {
    val colors = doctorVisitColors()
    Card(
        onClick = { onEdit(visit) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = visitDateFormatter.format(visit.date.atZone(ZoneId.systemDefault()).toLocalDate()),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onContainer,
                )
                Text(
                    text = visit.providerName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.doctor_visit_history_no_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onContainer,
                )
                visit.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (questionCount > 0 || visit.hasSnapshot()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (questionCount > 0) {
                            AssistChip(
                                onClick = { onEdit(visit) },
                                label = {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.doctor_visit_history_question_count,
                                            questionCount,
                                            questionCount,
                                        ),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(labelColor = colors.onContainer),
                            )
                        }
                        if (visit.hasSnapshot()) {
                            AssistChip(
                                onClick = { onEdit(visit) },
                                label = { Text(stringResource(R.string.doctor_visit_history_has_snapshot)) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = colors.onContainer),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { onDelete(visit) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.doctor_visit_delete),
                    tint = colors.onContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🩺", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.doctor_visit_history_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
