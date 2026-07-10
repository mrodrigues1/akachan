package com.babytracker.ui.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.domain.model.NapTransition
import com.babytracker.domain.model.RegressionInfo
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.ui.component.SleepIcon
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScheduleScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_schedule_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshSchedule) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sleep_schedule_refresh_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }
            }

            uiState.schedule != null -> {
                ScheduleContent(
                    schedule = uiState.schedule!!,
                    isRegressionExpanded = uiState.isRegressionExpanded,
                    onToggleRegression = viewModel::onToggleRegression,
                    modifier = Modifier.padding(padding)
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.sleep_schedule_onboarding),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleContent(
    schedule: SleepSchedule,
    isRegressionExpanded: Boolean,
    onToggleRegression: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Recompute the timeline only when the schedule inputs change, not on every recomposition
    // (e.g. each regression toggle). A fresh list each pass also defeated LazyColumn item reuse.
    val timelineItems = remember(
        schedule.napTimes,
        schedule.wakeWindows,
        schedule.bedtime,
        schedule.bedtimeWindow,
    ) {
        buildSleepTimelineItems(
            napTimes = schedule.napTimes,
            wakeWindows = schedule.wakeWindows,
            bedtime = schedule.bedtime,
            bedtimeWindow = schedule.bedtimeWindow
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            ScheduleHeader(
                schedule = schedule,
                hasRegression = schedule.regressionWarning != null,
                isRegressionExpanded = isRegressionExpanded,
                onToggleRegression = onToggleRegression
            )
        }

        item {
            Text(
                text = stringResource(R.string.sleep_today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        if (schedule.regressionWarning != null && isRegressionExpanded) {
            item { RegressionWarningCard(schedule.regressionWarning) }
        }

        itemsIndexed(
            timelineItems,
            // Stable identity (nap number, or the single bedtime row) so a schedule change moves
            // unchanged rows instead of re-laying-out the whole timeline.
            key = { _, item -> if (item.isBedtime) "bedtime" else "nap_${item.napNumber}" },
        ) { index, item ->
            SleepTimelineRow(
                item = item,
                isLast = index == timelineItems.lastIndex
            )
        }

        item { SleepSummaryCard(schedule) }

        if (schedule.napTransitionSuggestion != null) {
            item { NapTransitionCard(schedule.napTransitionSuggestion) }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ScheduleHeader(
    schedule: SleepSchedule,
    hasRegression: Boolean,
    isRegressionExpanded: Boolean,
    onToggleRegression: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatAge(schedule.ageInWeeks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeBadge(schedule.mode)
                    if (hasRegression) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onToggleRegression,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isRegressionExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = if (isRegressionExpanded) {
                                    stringResource(R.string.sleep_schedule_regression_hide_cd)
                                } else {
                                    stringResource(R.string.sleep_schedule_regression_show_cd)
                                },
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (schedule.isPersonalized) {
                    stringResource(R.string.sleep_schedule_personalized)
                } else {
                    stringResource(R.string.sleep_schedule_age_defaults)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ModeBadge(mode: ScheduleMode) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondary)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RegressionWarningCard(info: RegressionInfo) {
    val isDark = LocalDarkTheme.current
    val containerColor = if (isDark) WarningContainerAmberDark else WarningContainerAmber
    val onContainerColor = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(info.type.descriptionRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(info.type.nameRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = onContainerColor.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.sleep_schedule_typical_duration,
                    stringResource(info.type.durationRes()),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = onContainerColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SleepTimelineRow(
    item: SleepTimelineItem,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp),
        verticalAlignment = Alignment.Top
    ) {
        TimelineTimeColumn(item)
        TimelineRail(isLast = isLast)
        if (item.isBedtime) {
            BedtimeCard(item)
        } else {
            NapCard(item)
        }
    }
}

@Composable
private fun TimelineTimeColumn(item: SleepTimelineItem) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = formatLocalTime(item.startTime),
            style = MaterialTheme.typography.labelLarge,
            color = if (item.isBedtime) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TimelineRail(isLast: Boolean) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(68.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        if (!isLast) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
        )
    }
}

@Composable
private fun NapCard(item: SleepTimelineItem) {
    DenseTimelineCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineTextBlock(
                item = item,
                contentColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.trailing,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BedtimeCard(item: SleepTimelineItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineTextBlock(
                item = item,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                supportingColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.trailing,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun DenseTimelineCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = LocalDarkTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        content = content
    )
}

@Composable
private fun TimelineTextBlock(
    item: SleepTimelineItem,
    contentColor: Color,
    supportingColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isBedtime) {
            SleepIcon(modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = item.emoji,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        val label = if (item.isBedtime) {
            stringResource(R.string.sleep_schedule_bedtime)
        } else {
            stringResource(R.string.sleep_schedule_nap_number, item.napNumber)
        }
        val detail = when {
            item.isBedtime -> item.bedtimeWindow?.let {
                "${formatLocalTime(it.start)} - ${formatLocalTime(it.endInclusive)}"
            }.orEmpty()
            item.wakeWindow != null ->
                stringResource(R.string.sleep_schedule_after_awake, item.wakeWindow.formatDuration())
            else -> stringResource(R.string.sleep_schedule_suggested_nap)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isAdjusted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sleep_schedule_adjusted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = supportingColor
            )
        }
    }
}

@Composable
private fun SleepSummaryCard(schedule: SleepSchedule) {
    val isDark = LocalDarkTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sleep_schedule_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SummaryRow(
                label = stringResource(R.string.sleep_schedule_recommended_total),
                value = stringResource(
                    R.string.sleep_schedule_range,
                    schedule.totalSleepRecommendation.start.formatDuration(),
                    schedule.totalSleepRecommendation.endInclusive.formatDuration(),
                )
            )

            if (schedule.totalSleepLogged != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SummaryRow(
                    label = stringResource(R.string.sleep_schedule_average),
                    value = schedule.totalSleepLogged.formatDuration()
                )
            }

            if (schedule.lastFeedTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SummaryRow(
                    label = stringResource(R.string.sleep_schedule_last_feed),
                    value = schedule.lastFeedTime.formatTime12h()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            SummaryRow(
                label = stringResource(R.string.sleep_schedule_naps_per_day),
                value = "${schedule.napTimes.size}"
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun NapTransitionCard(transition: NapTransition) {
    val avgFormatted = String.format(java.util.Locale.getDefault(), "%.1f", transition.avgNapsPerDay)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sleep_schedule_nap_transition),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.sleep_nap_transition_suggestion,
                    transition.fromNaps,
                    transition.toNaps,
                    avgFormatted,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Formatting Helpers ---

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatLocalTime(time: LocalTime): String = time.format(timeFormatter)

internal data class SleepTimelineItem(
    val wakeWindow: Duration?,
    val startTime: LocalTime,
    val napNumber: Int,
    val trailing: String,
    val emoji: String,
    val bedtimeWindow: ClosedRange<LocalTime>? = null,
    val isAdjusted: Boolean = false,
    val isBedtime: Boolean = false,
)

internal fun buildSleepTimelineItems(
    napTimes: List<ScheduleEntry>,
    wakeWindows: List<Duration>,
    bedtime: LocalTime,
    bedtimeWindow: ClosedRange<LocalTime>,
): List<SleepTimelineItem> {
    val napItems = napTimes.mapIndexed { index, entry ->
        SleepTimelineItem(
            wakeWindow = wakeWindows.getOrNull(index),
            startTime = entry.startTime,
            napNumber = entry.napNumber,
            trailing = entry.duration.formatDuration(),
            emoji = "😴",
            isAdjusted = entry.isAdjusted
        )
    }

    return napItems + SleepTimelineItem(
        wakeWindow = wakeWindows.lastOrNull(),
        startTime = bedtime,
        napNumber = 0,
        trailing = formatLocalTime(bedtime),
        emoji = "",
        bedtimeWindow = bedtimeWindow,
        isBedtime = true
    )
}

/** Months and remaining whole weeks for an age in weeks. Pure for unit testing; the screen
 * renders it via localized plural resources in [formatAge]. */
internal fun ageBreakdown(ageInWeeks: Int): Pair<Int, Int> {
    val totalDays = ageInWeeks * 7
    val months = totalDays / 30
    val remainingWeeks = (totalDays - months * 30) / 7
    return months to remainingWeeks
}

@Composable
internal fun formatAge(ageInWeeks: Int): String {
    val (months, remainingWeeks) = ageBreakdown(ageInWeeks)
    return when {
        months == 0 -> pluralStringResource(R.plurals.sleep_age_weeks_old, ageInWeeks, ageInWeeks)
        remainingWeeks == 0 -> pluralStringResource(R.plurals.sleep_age_months_old, months, months)
        else -> stringResource(R.string.sleep_age_months_weeks_old, months, remainingWeeks)
    }
}

private fun Instant.formatTime12h(): String {
    return DateTimeFormatter.ofPattern("h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(this)
}
