package com.babytracker.ui.sleep

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime

@Composable
internal fun SleepPredictionCard(
    state: SleepPredictionState,
    modifier: Modifier = Modifier,
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
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SLEEP PREDICTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            when (state) {
                is SleepPredictionState.Window -> WindowCardContent(window = state.window)
                is SleepPredictionState.NeedMoreData -> NeedMoreDataCardContent(progress = state.progress)
                SleepPredictionState.Overdue -> OverdueCardContent()
                SleepPredictionState.CueLed -> CueLedCardContent()
                SleepPredictionState.CurrentlySleeping -> CurrentlySleepingCardContent()
                SleepPredictionState.AfterActiveFeed -> AfterActiveFeedCardContent()
                is SleepPredictionState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun WindowCardContent(window: SleepWindow) {
    var safetyExpanded by remember { mutableStateOf(false) }
    val timeRange = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}"
    val primary = "Next sleep ~${window.bestEstimate.formatTime()} · $timeRange"
    Column {
        Text(
            text = primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = primary },
        )
        if (window.confidence == Confidence.LOW) {
            Text(
                text = "low confidence",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
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
private fun NeedMoreDataCardContent(progress: EvidenceProgress) {
    Column {
        LinearProgressIndicator(
            progress = { progress.completedIntervals.toFloat() / progress.requiredIntervals.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = progress.hint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OverdueCardContent() {
    Text(
        text = "Watch for cues — next opportunity soon",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CueLedCardContent() {
    Text(
        text = "Watching baby's cues — no fixed window right now",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CurrentlySleepingCardContent() {
    Text(
        text = "Baby is sleeping",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AfterActiveFeedCardContent() {
    Text(
        text = "Feeding now — next sleep window appears after feed ends",
        style = MaterialTheme.typography.bodyMedium,
    )
}
