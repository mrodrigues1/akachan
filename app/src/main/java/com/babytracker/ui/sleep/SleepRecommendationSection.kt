package com.babytracker.ui.sleep

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SLEEP RECOMMENDATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (state is SleepPredictionState.Window) {
                    ConfidenceDots(confidence = state.window.confidence)
                }
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
    var safetyExpanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "~${window.bestEstimate.formatTime()}",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f),
        )
        if (window.reasons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            window.reasons.forEach { reason ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp, end = 6.dp)
                            .size(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
        val nextNap = schedule?.napTimes?.firstOrNull { it.startTime >= now }
        if (nextNap != null) {
            Spacer(Modifier.height(12.dp))
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
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { safetyExpanded = !safetyExpanded }
                .semantics {
                    contentDescription =
                        if (safetyExpanded) "Collapse safe-sleep tip" else "Expand safe-sleep tip"
                },
        ) {
            Text(
                text = "Safe sleep",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = if (safetyExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
        AnimatedVisibility(visible = safetyExpanded) {
            Text(
                text = window.safetyPrompt,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ConfidenceDots(confidence: Confidence, modifier: Modifier = Modifier) {
    val filledCount = when (confidence) {
        Confidence.LOW -> 1
        Confidence.MEDIUM -> 2
        Confidence.HIGH -> 3
    }
    val label = when (confidence) {
        Confidence.LOW -> "Low confidence prediction"
        Confidence.MEDIUM -> "Medium confidence prediction"
        Confidence.HIGH -> "High confidence prediction"
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        repeat(3) { i ->
            val filled = i < filledCount
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (filled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun NeedMoreDataSectionContent(progress: EvidenceProgress) {
    val total = progress.requiredIntervals.coerceAtMost(7)
    val filled = progress.completedIntervals.coerceAtMost(total)
    val a11yLabel = "Learning your baby's patterns. $filled of $total sleep sessions recorded."

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = progress.hint,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = a11yLabel },
        ) {
            repeat(total) { i ->
                val isFilled = i < filled
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isFilled) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                            },
                            shape = CircleShape,
                        ),
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = "$filled of $total sleep sessions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f),
            )
        }
        if (progress.localDays in 1 until progress.requiredLocalDays) {
            Text(
                text = "Day ${progress.localDays} of ${progress.requiredLocalDays}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    text: String,
    iconAlpha: Float = 1f,
    textAlpha: Float = 1f,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = iconAlpha),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = textAlpha),
        )
    }
}

@Composable
private fun OverdueSectionContent() {
    StatusRow(
        icon = Icons.Outlined.HourglassEmpty,
        text = "Watch for cues — the window may open soon",
    )
}

@Composable
private fun CueLedSectionContent() {
    StatusRow(
        icon = Icons.Outlined.Visibility,
        text = "Watching baby's cues — no fixed window right now",
    )
}

@Composable
private fun CurrentlySleepingSectionContent() {
    StatusRow(
        icon = Icons.Outlined.Bedtime,
        text = "Baby is sleeping",
        iconAlpha = 0.7f,
        textAlpha = 0.8f,
    )
}

@Composable
private fun AfterActiveFeedSectionContent() {
    StatusRow(
        icon = Icons.Outlined.ChildCare,
        text = "Feeding now — sleep window appears after feed ends",
    )
}
