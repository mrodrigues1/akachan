package com.babytracker.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepPredictionCard(
    state: SleepPredictionState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    if (state is SleepPredictionState.Unavailable) return

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Sleep prediction. Open sleep screen." },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SLEEP PREDICTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (state is SleepPredictionState.Window) {
                    ConfidenceDots(confidence = state.window.confidence)
                }
            }
            Spacer(Modifier.height(10.dp))
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
    Column {
        Text(
            text = "Around ${window.bestEstimate.formatTime()}",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics {
                contentDescription = "Next sleep around ${window.bestEstimate.formatTime()}"
            },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f),
        )
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
private fun NeedMoreDataCardContent(progress: EvidenceProgress) {
    val total = progress.requiredIntervals.coerceAtMost(7)
    val filled = progress.completedIntervals.coerceAtMost(total)
    val a11yLabel = "Learning your baby's patterns. $filled of $total sleep sessions recorded."

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = progress.hint,
            style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = textAlpha),
        )
    }
}

@Composable
private fun OverdueCardContent() {
    StatusRow(
        icon = Icons.Outlined.HourglassEmpty,
        text = "Watch for cues — the window may open soon",
    )
}

@Composable
private fun CueLedCardContent() {
    StatusRow(
        icon = Icons.Outlined.Visibility,
        text = "Watching baby's cues — no fixed window right now",
    )
}

@Composable
private fun CurrentlySleepingCardContent() {
    StatusRow(
        icon = Icons.Outlined.Bedtime,
        text = "Baby is sleeping",
        iconAlpha = 0.7f,
        textAlpha = 0.8f,
    )
}

@Composable
private fun AfterActiveFeedCardContent() {
    StatusRow(
        icon = Icons.Outlined.ChildCare,
        text = "Feeding now — sleep window appears after feed ends",
    )
}
