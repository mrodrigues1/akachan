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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.model.RegressionInfo
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepSchedule
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
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sleep Schedule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshSchedule) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                        text = "Complete onboarding to generate a schedule",
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
    val timelineItems = buildSleepTimelineItems(
        napTimes = schedule.napTimes,
        wakeWindows = schedule.wakeWindows,
        bedtime = schedule.bedtime,
        bedtimeWindow = schedule.bedtimeWindow
    )

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
                text = "TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        if (schedule.regressionWarning != null && isRegressionExpanded) {
            item { RegressionWarningCard(schedule.regressionWarning) }
        }

        itemsIndexed(timelineItems) { index, item ->
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
                                    "Hide regression info"
                                } else {
                                    "Show regression info"
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
                    "Personalized from your logged data"
                } else {
                    "Using age-based defaults"
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
            text = mode.label,
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
                text = info.description,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = info.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = onContainerColor.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Typical duration: ${info.durationWeeks}",
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
        Text(
            text = item.emoji,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isAdjusted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "adjusted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.detail,
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
                text = "Sleep Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SummaryRow(
                label = "Recommended total sleep",
                value = "${schedule.totalSleepRecommendation.start.formatDuration()} - " +
                    "${schedule.totalSleepRecommendation.endInclusive.formatDuration()}"
            )

            if (schedule.totalSleepLogged != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SummaryRow(
                    label = "Your baby's average (7 days)",
                    value = schedule.totalSleepLogged.formatDuration()
                )
            }

            if (schedule.lastFeedTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SummaryRow(
                    label = "Last feed",
                    value = schedule.lastFeedTime.formatTime12h()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            SummaryRow(
                label = "Naps per day",
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
private fun NapTransitionCard(suggestion: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Nap Transition",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = suggestion,
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
    val label: String,
    val detail: String,
    val trailing: String,
    val emoji: String,
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
        val wakeWindow = wakeWindows.getOrNull(index)
        SleepTimelineItem(
            wakeWindow = wakeWindow,
            startTime = entry.startTime,
            label = entry.label,
            detail = wakeWindow?.let { "After ${it.formatDuration()} awake" } ?: "Suggested nap",
            trailing = entry.duration.formatDuration(),
            emoji = entry.emoji,
            isAdjusted = entry.isAdjusted
        )
    }

    return napItems + SleepTimelineItem(
        wakeWindow = wakeWindows.lastOrNull(),
        startTime = bedtime,
        label = "Bedtime",
        detail = "${formatLocalTime(bedtimeWindow.start)} - ${formatLocalTime(bedtimeWindow.endInclusive)}",
        trailing = formatLocalTime(bedtime),
        emoji = "\uD83C\uDF19",
        isBedtime = true
    )
}

internal fun formatAge(ageInWeeks: Int): String {
    val totalDays = ageInWeeks * 7
    val months = totalDays / 30
    val remainingWeeks = (totalDays - months * 30) / 7
    return when {
        months == 0 -> "$ageInWeeks weeks old"
        remainingWeeks == 0 -> "$months months old"
        else -> "$months months, $remainingWeeks weeks old"
    }
}

private fun Instant.formatTime12h(): String {
    return DateTimeFormatter.ofPattern("h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(this)
}
