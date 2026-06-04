package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")

@Composable
internal fun SleepRecommendationSection(
    state: SleepPredictionState,
    schedule: SleepSchedule?,
    modifier: Modifier = Modifier,
    now: LocalTime = LocalTime.now(),
) {
    if (state is SleepPredictionState.Unavailable) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SLEEP RECOMMENDATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                is SleepPredictionState.Window ->
                    WindowSectionContent(window = state.window, schedule = schedule, now = now)
                is SleepPredictionState.NeedMoreData ->
                    NeedMoreDataSectionContent(progress = state.progress)
                SleepPredictionState.Overdue -> OverdueSectionContent()
                SleepPredictionState.CueLed -> CueLedSectionContent()
                SleepPredictionState.CurrentlySleeping -> CurrentlySleepingSectionContent()
                SleepPredictionState.AfterActiveFeed -> AfterActiveFeedSectionContent()
                is SleepPredictionState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun WindowSectionContent(window: SleepWindow, schedule: SleepSchedule?, now: LocalTime) {
    Column {
        Text(
            text = "~${window.bestEstimate.formatTime()}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )

        Spacer(Modifier.height(8.dp))
        ConfidenceBadge(confidence = window.confidence)

        if (window.reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            window.reasons.forEach { reason ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(text = reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val nextNap = schedule?.napTimes?.firstOrNull { it.startTime >= now }
        if (nextNap != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "DAY PLAN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = nextNap.startTime.format(TIME_FORMATTER),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "NEXT WINDOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "~${window.bestEstimate.formatTime()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        window.feedPrompt?.let { prompt ->
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = window.safetyPrompt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun ConfidenceBadge(confidence: Confidence) {
    val (bg, fg) = when (confidence) {
        Confidence.LOW ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Confidence.MEDIUM ->
            MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
        Confidence.HIGH ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = bg) {
        Text(
            text = confidence.name,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun NeedMoreDataSectionContent(progress: EvidenceProgress) {
    Column {
        LinearProgressIndicator(
            progress = { progress.completedIntervals.toFloat() / progress.requiredIntervals.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(text = progress.hint, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OverdueSectionContent() {
    Text(
        text = "Watch for sleep cues — the window may open soon.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CueLedSectionContent() {
    Text(
        text = "Watching baby's cues — no fixed window right now.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CurrentlySleepingSectionContent() {
    Text(
        text = "Baby is currently sleeping. Next window prediction will appear after wake.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AfterActiveFeedSectionContent() {
    Text(
        text = "A feed is in progress. Sleep window prediction appears after the feed ends.",
        style = MaterialTheme.typography.bodyMedium,
    )
}
