package com.babytracker.ui.vaccine

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.isOverdue
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.VaccinePalette
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.ui.theme.vaccineColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VaccineDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    dashboardViewModel: VaccineDashboardViewModel = hiltViewModel(),
    formViewModel: VaccineViewModel = hiltViewModel(),
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val formState by formViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSheet by remember { mutableStateOf(false) }

    // A successful save closes the add/edit sheet.
    LaunchedEffect(formState.saved) { if (formState.saved) showSheet = false }

    val markedMessage = stringResource(R.string.vaccine_marked_given)
    val undoLabel = stringResource(R.string.vaccine_undo)
    LaunchedEffect(state.lastMarkedGiven) {
        state.lastMarkedGiven ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = markedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> dashboardViewModel.undoMarkGiven()
            SnackbarResult.Dismissed -> dashboardViewModel.onMarkGivenConsumed()
        }
    }

    if (showSheet) {
        VaccineSheet(
            state = formState,
            onNameChange = formViewModel::onNameChange,
            onDoseChange = formViewModel::onDoseChange,
            onModeChange = formViewModel::onModeChange,
            onDateChange = formViewModel::onDateChange,
            onNotesChange = formViewModel::onNotesChange,
            onConfirm = formViewModel::onSave,
            onDismiss = { showSheet = false },
        )
    }

    VaccineDashboardContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAddVaccine = {
            formViewModel.onStartAdd()
            showSheet = true
        },
        onEditRecord = { record ->
            formViewModel.loadForEdit(record)
            showSheet = true
        },
        onMarkGiven = dashboardViewModel::markGiven,
        onRetry = dashboardViewModel::onRetry,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineDashboardContent(
    state: VaccineDashboardUiState,
    snackbarHostState: SnackbarHostState,
    onAddVaccine: () -> Unit,
    onEditRecord: (VaccineRecord) -> Unit,
    onMarkGiven: (VaccineRecord) -> Unit,
    onRetry: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = vaccineColors()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vaccine_tile_label)) },
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
                            contentDescription = stringResource(R.string.vaccine_view_history),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.vaccine_reminder_settings_button),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = { AddVaccineBar(colors = colors, onAddVaccine = onAddVaccine) },
    ) { padding ->
        when {
            state.isLoading -> DashboardSkeleton(modifier = Modifier.padding(padding))
            state.isError -> DashboardError(onRetry = onRetry, modifier = Modifier.padding(padding))
            state.isFirstRun -> VaccineEmpty(modifier = Modifier.padding(padding))
            else -> DashboardBody(
                state = state,
                colors = colors,
                onEditRecord = onEditRecord,
                onMarkGiven = onMarkGiven,
                onViewAll = onNavigateToHistory,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DashboardBody(
    state: VaccineDashboardUiState,
    colors: VaccinePalette,
    onEditRecord: (VaccineRecord) -> Unit,
    onMarkGiven: (VaccineRecord) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The hero owns the single most urgent record; the schedule list shows everything after it,
    // so a vaccine is never rendered twice.
    val heroRecord = state.schedule.firstOrNull()
    val remainingSchedule = state.schedule.drop(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        when {
            heroRecord != null && state.mostOverdue != null ->
                OverdueHero(
                    record = heroRecord,
                    overdueDays = state.mostOverdueDays ?: 0,
                    extraOverdue = state.overdueCount - 1,
                    onEdit = { onEditRecord(heroRecord) },
                    onMarkGiven = { onMarkGiven(heroRecord) },
                )

            heroRecord != null ->
                NextUpHero(
                    record = heroRecord,
                    daysUntil = state.nextInDays ?: 0,
                    colors = colors,
                    onEdit = { onEditRecord(heroRecord) },
                    onMarkGiven = { onMarkGiven(heroRecord) },
                )

            else -> CaughtUpHero()
        }

        if (remainingSchedule.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            ScheduleSection(
                records = remainingSchedule,
                now = state.now,
                colors = colors,
                onEditRecord = onEditRecord,
                onMarkGiven = onMarkGiven,
            )
        }

        if (state.recentlyGiven.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            RecentlyGivenSection(
                records = state.recentlyGiven,
                colors = colors,
                onEditRecord = onEditRecord,
                onViewAll = onViewAll,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Filled indigo hero for the soonest upcoming vaccine. Tap to edit; the ghost button marks it given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextUpHero(
    record: VaccineRecord,
    daysUntil: Int,
    colors: VaccinePalette,
    onEdit: () -> Unit,
    onMarkGiven: () -> Unit,
) {
    // Light fills with a dark accent (Indigo700), so 0.85-alpha white still clears AA. Dark fills with
    // a light accent (Indigo200) where the same dim drops below AA, so dark keeps full onAccent.
    val secondary = if (LocalDarkTheme.current) colors.onAccent else colors.onAccent.copy(alpha = 0.85f)
    HeroCard(
        containerColor = colors.accent,
        editLabel = stringResource(R.string.vaccine_edit_content_description, record.name),
        onEdit = onEdit,
    ) {
        HeroLabel(text = stringResource(R.string.vaccine_next_up), color = secondary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = countdownLabel(daysUntil),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onAccent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = record.nameWithDose(),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onAccent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        record.scheduledDate?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.vaccine_scheduled_for, it.toDateLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        HeroMarkGivenButton(record = record, contentColor = colors.onAccent, onMarkGiven = onMarkGiven)
    }
}

/** Soft-amber hero for the most overdue vaccine: urgent but supportive, never alarming. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverdueHero(
    record: VaccineRecord,
    overdueDays: Int,
    extraOverdue: Int,
    onEdit: () -> Unit,
    onMarkGiven: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val container = if (isDark) WarningContainerAmberDark else WarningContainerAmber
    val onContainer = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber
    HeroCard(
        containerColor = container,
        editLabel = stringResource(R.string.vaccine_edit_content_description, record.name),
        onEdit = onEdit,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = onContainer, modifier = Modifier.size(16.dp))
            HeroLabel(text = stringResource(R.string.vaccine_overdue_badge), color = onContainer)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = pluralStringResource(R.plurals.vaccine_overdue_days, overdueDays, overdueDays),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = onContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = record.nameWithDose(),
            style = MaterialTheme.typography.bodyLarge,
            color = onContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (extraOverdue > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralStringResource(R.plurals.vaccine_more_overdue, extraOverdue, extraOverdue),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        HeroMarkGivenButton(record = record, contentColor = onContainer, onMarkGiven = onMarkGiven)
    }
}

/** Quiet outlined hero shown when nothing is scheduled but vaccines have been logged before. */
@Composable
private fun CaughtUpHero() {
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
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.vaccine_caught_up_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.vaccine_caught_up_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroCard(
    containerColor: Color,
    editLabel: String,
    onEdit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onEdit,
        // Label the card's click action so TalkBack announces "Edit DTP", not a bare "activate".
        modifier = Modifier
            .fillMaxWidth()
            .semantics { onClick(label = editLabel, action = null) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun HeroLabel(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HeroMarkGivenButton(
    record: VaccineRecord,
    contentColor: Color,
    onMarkGiven: () -> Unit,
) {
    // The visible label is the generic "Mark as given"; name the record for TalkBack so the spoken
    // target is unambiguous when the hero scrolls out of the reading order.
    val markLabel = stringResource(R.string.vaccine_mark_given_content_description, record.name)
    OutlinedButton(
        onClick = onMarkGiven,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = markLabel },
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.vaccine_mark_given), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ScheduleSection(
    records: List<VaccineRecord>,
    now: Instant,
    colors: VaccinePalette,
    onEditRecord: (VaccineRecord) -> Unit,
    onMarkGiven: (VaccineRecord) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(
            text = stringResource(R.string.vaccine_history_upcoming),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.animateContentSize()) {
            records.forEach { record ->
                ScheduleRow(
                    record = record,
                    overdue = record.isOverdue(now),
                    colors = colors,
                    onEdit = { onEditRecord(record) },
                    onMarkGiven = { onMarkGiven(record) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    record: VaccineRecord,
    overdue: Boolean,
    colors: VaccinePalette,
    onEdit: () -> Unit,
    onMarkGiven: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val overdueColor = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber
    val dotColor = if (overdue) overdueColor else colors.accent
    val editLabel = stringResource(R.string.vaccine_edit_content_description, record.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = dotColor, filled = false)
        Spacer(Modifier.size(12.dp))
        val date = record.scheduledDate?.toDateLabel().orEmpty()
        val subtitle = if (overdue) {
            "${stringResource(R.string.vaccine_overdue_badge)} · $date"
        } else {
            date
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.nameWithDose(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                // Color carries the urgency; the text stays factual.
                color = if (overdue) overdueColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMarkGiven) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.vaccine_mark_given_content_description, record.name),
                tint = colors.accent,
            )
        }
    }
}

@Composable
private fun RecentlyGivenSection(
    records: List<VaccineRecord>,
    colors: VaccinePalette,
    onEditRecord: (VaccineRecord) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                text = stringResource(R.string.vaccine_recently_given),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onViewAll) {
                Text(text = stringResource(R.string.vaccine_view_all), color = colors.accent)
            }
        }
        Spacer(Modifier.height(4.dp))
        records.forEach { record ->
            GivenRow(record = record, colors = colors, onEdit = { onEditRecord(record) })
        }
    }
}

@Composable
private fun GivenRow(
    record: VaccineRecord,
    colors: VaccinePalette,
    onEdit: () -> Unit,
) {
    val editLabel = stringResource(R.string.vaccine_edit_content_description, record.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = colors.accent, filled = true)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.nameWithDose(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (record.administeredDate ?: record.createdAt).let {
                Text(
                    text = it.toDateLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, filled: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.18f)),
    )
}

@Composable
private fun AddVaccineBar(
    colors: VaccinePalette,
    onAddVaccine: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onAddVaccine,
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
                Text(text = stringResource(R.string.vaccine_add_title), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun VaccineEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("💉", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.vaccine_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.vaccine_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DashboardError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = vaccineColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
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

/**
 * Calm placeholder shown until the first Room emission lands, so a cold start no longer flashes the
 * empty state for a frame. Static low-emphasis blocks (no shimmer) keep it quiet.
 */
@Composable
private fun DashboardSkeleton(modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
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
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

/** Day-granularity countdown to an upcoming vaccine: Today / Tomorrow / In N days. */
@Composable
private fun countdownLabel(days: Int): String = when {
    days <= 0 -> stringResource(R.string.vaccine_tile_today)
    days == 1 -> stringResource(R.string.vaccine_countdown_tomorrow)
    else -> pluralStringResource(R.plurals.vaccine_countdown_in_days, days, days)
}

private fun VaccineRecord.nameWithDose(): String =
    doseLabel?.takeIf { it.isNotBlank() }?.let { "$name · $it" } ?: name

private val dashboardDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private fun Instant.toDateLabel(): String =
    dashboardDateFormatter.format(atZone(ZoneId.systemDefault()).toLocalDate())
