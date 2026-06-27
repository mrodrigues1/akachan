package com.babytracker.ui.doctorvisit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.ui.theme.DoctorVisitPalette
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.doctorVisitColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val snackbarHostState = remember { SnackbarHostState() }

    val answeredMessage = stringResource(R.string.visit_questions_answered)
    val undoLabel = stringResource(R.string.visit_questions_undo)
    LaunchedEffect(state.lastAnswered) {
        state.lastAnswered ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = answeredMessage,
            actionLabel = undoLabel,
            // The one recoverable mistake on the screen; give a distracted parent longer to catch it.
            duration = SnackbarDuration.Long,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.onUndoAnswered()
            SnackbarResult.Dismissed -> viewModel.onUndoAnsweredConsumed()
        }
    }

    DoctorVisitDashboardContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onDraftChange = viewModel::onDraftChange,
        onAddQuestion = viewModel::onAddQuestion,
        onToggleAnswered = viewModel::onToggleAnswered,
        onRetry = viewModel::onRetry,
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
    snackbarHostState: SnackbarHostState,
    onDraftChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onToggleAnswered: (Long) -> Unit,
    onRetry: () -> Unit,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        if (state.isLoading) {
            DashboardSkeleton(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        if (state.isError) {
            DashboardError(onRetry = onRetry, modifier = Modifier.padding(padding))
            return@Scaffold
        }
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
                daysUntil = state.nextVisitInDays ?: 0,
                colors = colors,
                onEditVisit = onEditVisit,
            )

            if (state.upcomingVisits.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                UpcomingSection(
                    visits = state.upcomingVisits,
                    onEditVisit = onEditVisit,
                )
            }

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
    daysUntil: Int,
    colors: DoctorVisitPalette,
    onEditVisit: (Long) -> Unit,
) {
    if (visit == null) {
        NoUpcomingVisit()
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
    val countdown = countdownLabel(daysUntil)
    val whenLine = "${dateFormatter.format(visitDate)} · ${timeFormatter.format(visitDate)}"
    // The visible "date · time" uses a middot; give TalkBack a comma so it doesn't read "dot".
    val whenSpoken = "${dateFormatter.format(visitDate)}, ${timeFormatter.format(visitDate)}"
    val editLabel = stringResource(R.string.doctor_visit_edit_title)
    // Light mode fills with a dark accent (BlueGrey700), so 0.85-alpha white still clears WCAG AA.
    // Dark mode fills with a light accent (BlueGrey300) where the same dim drops below AA, so the
    // dark scheme keeps full onAccent and leans on weight/size for the secondary hierarchy.
    val secondary = if (LocalDarkTheme.current) {
        colors.onAccent
    } else {
        colors.onAccent.copy(alpha = 0.85f)
    }

    Card(
        onClick = { onEditVisit(visit.id) },
        // Label the card's click action so TalkBack announces "Edit visit", not a bare "activate".
        modifier = Modifier
            .fillMaxWidth()
            .semantics { onClick(label = editLabel, action = null) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.accent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    text = stringResource(R.string.doctor_visit_next_visit),
                    color = secondary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                // Visible affordance only; the card's onClick semantics carries the "Edit visit" label.
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = countdown,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = whenLine,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onAccent,
                modifier = Modifier.semantics { contentDescription = whenSpoken },
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

@Composable
private fun NoUpcomingVisit() {
    // Informational, not actionable: the persistent bottom bar is the single Add-visit CTA, so this
    // card no longer duplicates it (avoids two identical Slate add actions a thumb-width apart).
    OutlinedCard(
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
            // Preview is capped, so when more are open than shown, surface the total here
            // (number only, no new copy) instead of silently truncating the list.
            val sectionTitle = stringResource(R.string.visit_questions_title)
            val truncated = openQuestionCount > questions.size
            // The "· N" middot reads as "dot" on TalkBack, so describe the count in words instead.
            val openQuestionsSpoken = pluralStringResource(
                R.plurals.doctor_visit_tile_open_questions,
                openQuestionCount,
                openQuestionCount,
            )
            SectionLabel(
                text = if (truncated) "$sectionTitle · $openQuestionCount" else sectionTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        heading()
                        if (truncated) contentDescription = openQuestionsSpoken
                    },
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
            // Cap the list height so a long question backlog scrolls within the section instead of
            // pushing the rest of the dashboard (and the add-question field) off screen.
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                questions.forEach { question ->
                    QuestionPreviewRow(
                        question = question,
                        colors = colors,
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
            // Keep the focused field in the Slate domain instead of the global pink primary.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                cursorColor = colors.accent,
            ),
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
    colors: DoctorVisitPalette,
    onToggleAnswered: () -> Unit,
) {
    // The 2-line preview ellipsizes long questions, so tapping the text opens the full text.
    var showFull by remember { mutableStateOf(false) }
    val viewLabel = stringResource(R.string.visit_questions_expand)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Guarantee the brand's 48dp minimum even when the question is a single short line.
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = question.answered,
            onCheckedChange = { onToggleAnswered() },
        )
        Text(
            text = question.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = viewLabel) { showFull = true }
                .padding(vertical = 12.dp),
        )
    }
    if (showFull) {
        QuestionDetailDialog(
            question = question,
            colors = colors,
            onDismiss = { showFull = false },
        )
    }
}

@Composable
private fun QuestionDetailDialog(
    question: VisitQuestion,
    colors: DoctorVisitPalette,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                // Keep the action in the Slate domain instead of the global pink primary.
                Text(stringResource(R.string.close), color = colors.accent)
            }
        },
        title = { Text(stringResource(R.string.visit_questions_title)) },
        text = {
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyLarge,
                // Long questions can exceed the dialog height; let them scroll.
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
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
            VisitRow(visit = visit, onEditVisit = { onEditVisit(visit.id) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Lists every scheduled visit beyond the hero's nearest one, so a parent with several booked
 *  appointments sees them all here instead of only in the History screen. */
@Composable
private fun UpcomingSection(
    visits: List<DoctorVisit>,
    onEditVisit: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(
            text = stringResource(R.string.doctor_visit_history_upcoming),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        visits.forEach { visit ->
            VisitRow(visit = visit, onEditVisit = { onEditVisit(visit.id) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VisitRow(
    visit: DoctorVisit,
    onEditVisit: () -> Unit,
) {
    val dateFormatter = remember(Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    val editLabel = stringResource(R.string.doctor_visit_edit_title)
    Card(
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
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
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
            IconButton(onClick = onEditVisit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = editLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Column {
            // Hairline detaches the persistent CTA from content scrolling underneath it.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onAddVisit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp),
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
}

/**
 * Replaces the body when an upstream flow throws, so a load failure offers a calm retry instead of
 * a skeleton that never resolves. Copy is reassuring, not alarming, per the product's tone.
 */
@Composable
private fun DashboardError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = doctorVisitColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.doctor_visit_load_error),
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

/**
 * Calm placeholder shown until the first [combine] emission lands, so a cold start no longer
 * flashes the "no upcoming visit" / "no questions" empty states for a frame. Static low-emphasis
 * blocks (no shimmer) keep it quiet, per the "calm over clever" principle.
 */
@Composable
private fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val loadingLabel = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            // Otherwise the placeholder blocks announce nothing; tell TalkBack the screen is loading.
            .semantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(MaterialTheme.shapes.large)
                .background(block),
        )
        Spacer(Modifier.height(24.dp))
        repeat(3) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == 0) 0.4f else 1f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(block),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    com.babytracker.ui.component.SectionLabel(
        text = text.uppercase(Locale.getDefault()),
        modifier = modifier,
        color = color,
    )
}

/** Day-granularity countdown to the next appointment: Today / Tomorrow / in N days. The day count
 *  is computed in the ViewModel against the injected clock, so it stays consistent with isUpcoming. */
@Composable
private fun countdownLabel(days: Int): String = when {
    days <= 0 -> stringResource(R.string.doctor_visit_countdown_today)
    days == 1 -> stringResource(R.string.doctor_visit_countdown_tomorrow)
    else -> pluralStringResource(R.plurals.doctor_visit_countdown_in_days, days, days)
}
