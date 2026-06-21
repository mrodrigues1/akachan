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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.hasSnapshot
import com.babytracker.ui.theme.DoctorVisitPalette
import com.babytracker.ui.theme.doctorVisitColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
                        SectionHeader(stringResource(R.string.doctor_visit_history_upcoming), colors.onContainer)
                    }
                    items(state.upcoming, key = { "u_${it.id}" }) { visit ->
                        VisitRow(visit, state.questionCounts[visit.id] ?: 0, colors, onEdit, onDelete)
                    }
                }
                if (state.past.isNotEmpty()) {
                    item(key = "past_header") {
                        SectionHeader(stringResource(R.string.doctor_visit_history_past), colors.onContainer)
                    }
                    items(state.past, key = { "p_${it.id}" }) { visit ->
                        VisitRow(visit, state.questionCounts[visit.id] ?: 0, colors, onEdit, onDelete, past = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitRow(
    visit: DoctorVisit,
    questionCount: Int,
    colors: DoctorVisitPalette,
    onEdit: (DoctorVisit) -> Unit,
    onDelete: (DoctorVisit) -> Unit,
    past: Boolean = false,
) {
    val container = if (past) MaterialTheme.colorScheme.surfaceVariant else colors.container
    val onContainer = if (past) MaterialTheme.colorScheme.onSurface else colors.onContainer
    val secondary = onContainer.copy(alpha = 0.8f)
    val editLabel = stringResource(R.string.doctor_visit_edit_title)
    // Keyed on locale so a runtime language switch rebuilds the formatter instead of leaving the
    // date frozen to the load-time locale (a top-level val would never rebuild).
    val dateFormatter = remember(Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    }
    Card(
        onClick = { onEdit(visit) },
        // Label the card's click action so TalkBack announces "Edit visit", not a bare "activate".
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { onClick(label = editLabel, action = null) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormatter.format(visit.date.atZone(ZoneId.systemDefault()).toLocalDate()),
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                )
                Text(
                    text = visit.providerName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.doctor_visit_history_no_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                visit.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (questionCount > 0 || visit.hasSnapshot()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (questionCount > 0) {
                            VisitMeta(
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                text = pluralStringResource(
                                    R.plurals.doctor_visit_history_question_count,
                                    questionCount,
                                    questionCount,
                                ),
                                color = secondary,
                            )
                        }
                        if (visit.hasSnapshot()) {
                            VisitMeta(
                                icon = Icons.Outlined.Description,
                                text = stringResource(R.string.doctor_visit_history_has_snapshot),
                                color = secondary,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { onDelete(visit) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.doctor_visit_delete),
                    tint = onContainer,
                )
            }
        }
    }
}

@Composable
private fun VisitMeta(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
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
