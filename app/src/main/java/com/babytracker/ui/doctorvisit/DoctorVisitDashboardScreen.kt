package com.babytracker.ui.doctorvisit

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.ui.theme.DoctorVisitPalette
import com.babytracker.ui.theme.doctorVisitColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun DoctorVisitDashboardScreen(
    onAddVisit: () -> Unit,
    onEditVisit: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onManageQuestions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorVisitDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DoctorVisitDashboardContent(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onAddQuestion = viewModel::onAddQuestion,
        onToggleAnswered = viewModel::onToggleAnswered,
        onAddVisit = onAddVisit,
        onEditVisit = onEditVisit,
        onNavigateToHistory = onNavigateToHistory,
        onManageQuestions = onManageQuestions,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorVisitDashboardContent(
    state: DoctorVisitDashboardUiState,
    onDraftChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onToggleAnswered: (Long) -> Unit,
    onAddVisit: () -> Unit,
    onEditVisit: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onManageQuestions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = doctorVisitColors()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doctor_visit_tile_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = stringResource(R.string.doctor_visit_view_history),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(
                                R.string.doctor_visit_reminder_settings_button,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            AddVisitBar(colors = colors, onAddVisit = onAddVisit)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            NextVisitHero(
                visit = state.nextVisit,
                openQuestionCount = state.openQuestionCount,
                colors = colors,
                onEditVisit = onEditVisit,
                onAddVisit = onAddVisit,
            )

            Spacer(Modifier.height(24.dp))

            QuestionsSection(
                draft = state.draft,
                questions = state.questions,
                openQuestionCount = state.openQuestionCount,
                colors = colors,
                onDraftChange = onDraftChange,
                onAddQuestion = onAddQuestion,
                onToggleAnswered = onToggleAnswered,
                onManageQuestions = onManageQuestions,
            )

            if (state.recentVisits.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                RecentSection(
                    visits = state.recentVisits,
                    colors = colors,
                    onEditVisit = onEditVisit,
                    onViewAll = onNavigateToHistory,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The hero: a filled Slate card for the next appointment, or a quiet outlined prompt when none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextVisitHero(
    visit: DoctorVisit?,
    openQuestionCount: Int,
    colors: DoctorVisitPalette,
    onEditVisit: (Long) -> Unit,
    onAddVisit: () -> Unit,
) {
    if (visit == null) {
        NoUpcomingVisit(onAddVisit = onAddVisit)
        return
    }

    val locale = Locale.getDefault()
    // Localized full date incl. weekday + clock-aware time ("Saturday, June 20, 2026 · 2:30 PM"
    // vs "sábado, 20 de junho de 2026 · 14:30"). Keyed on locale so a runtime language switch
    // rebuilds them instead of staying frozen to the load-time locale.
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val timeFormatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val zone = ZoneId.systemDefault()
    val visitDate = visit.date.atZone(zone)
    val countdown = countdownLabel(visitDate.toLocalDate())
    val whenLine = "${dateFormatter.format(visitDate)} · ${timeFormatter.format(visitDate)}"
    val secondary = colors.onAccent.copy(alpha = 0.85f)

    Card(
        onClick = { onEditVisit(visit.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.accent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionLabel(
                text = stringResource(R.string.doctor_visit_next_visit),
                color = secondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = countdown,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = whenLine,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onAccent,
            )
            visit.providerName?.takeIf { it.isNotBlank() }?.let { provider ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = provider,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (openQuestionCount > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.doctor_visit_tile_open_questions,
                            openQuestionCount,
                            openQuestionCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onAccent,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoUpcomingVisit(onAddVisit: () -> Unit) {
    OutlinedCard(
        onClick = onAddVisit,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Outlined.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.doctor_visit_no_upcoming_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.doctor_visit_no_upcoming_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionsSection(
    draft: String,
    questions: List<VisitQuestion>,
    openQuestionCount: Int,
    colors: DoctorVisitPalette,
    onDraftChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onToggleAnswered: (Long) -> Unit,
    onManageQuestions: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = stringResource(R.string.visit_questions_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (openQuestionCount > 0) {
                TextButton(onClick = onManageQuestions) {
                    Text(
                        text = stringResource(R.string.doctor_visit_manage_all),
                        color = colors.accent,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        QuestionCaptureRow(
            draft = draft,
            colors = colors,
            onDraftChange = onDraftChange,
            onAddQuestion = onAddQuestion,
        )

        Spacer(Modifier.height(4.dp))

        if (questions.isEmpty()) {
            Text(
                text = stringResource(R.string.visit_questions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        } else {
            Column(modifier = Modifier.animateContentSize()) {
                questions.forEach { question ->
                    QuestionPreviewRow(
                        question = question,
                        onToggleAnswered = { onToggleAnswered(question.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionCaptureRow(
    draft: String,
    colors: DoctorVisitPalette,
    onDraftChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val submit = {
        if (draft.isNotBlank()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onAddQuestion()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.visit_questions_add_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        FilledIconButton(
            onClick = submit,
            enabled = draft.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.visit_questions_add),
            )
        }
    }
}

@Composable
private fun QuestionPreviewRow(
    question: VisitQuestion,
    onToggleAnswered: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = question.answered,
                onValueChange = { onToggleAnswered() },
                role = Role.Checkbox,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The whole row carries the toggle semantics; the checkbox is visual only.
        Checkbox(checked = question.answered, onCheckedChange = null)
        Text(
            text = question.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentSection(
    visits: List<DoctorVisit>,
    colors: DoctorVisitPalette,
    onEditVisit: (Long) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = stringResource(R.string.doctor_visit_recent),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onViewAll) {
                Text(
                    text = stringResource(R.string.doctor_visit_view_all),
                    color = colors.accent,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        visits.forEach { visit ->
            RecentVisitRow(visit = visit, onEditVisit = { onEditVisit(visit.id) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentVisitRow(
    visit: DoctorVisit,
    onEditVisit: () -> Unit,
) {
    val dateFormatter = remember(Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    Card(
        onClick = onEditVisit,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormatter.format(visit.date.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = visit.providerName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.doctor_visit_history_no_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AddVisitBar(
    colors: DoctorVisitPalette,
    onAddVisit: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Button(
            onClick = onAddVisit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.doctor_visit_add),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

/** Day-granularity countdown to the next appointment: Today / Tomorrow / in N days. */
@Composable
private fun countdownLabel(visitDate: LocalDate): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), visitDate)
    return when {
        days <= 0L -> stringResource(R.string.doctor_visit_countdown_today)
        days == 1L -> stringResource(R.string.doctor_visit_countdown_tomorrow)
        else -> pluralStringResource(
            R.plurals.doctor_visit_countdown_in_days,
            days.toInt(),
            days.toInt(),
        )
    }
}
