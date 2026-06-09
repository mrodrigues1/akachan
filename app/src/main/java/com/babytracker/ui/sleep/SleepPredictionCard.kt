package com.babytracker.ui.sleep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepPredictionCard(
    state: SleepPredictionState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var visibleState by remember { mutableStateOf(state) }
    if (state !is SleepPredictionState.Unavailable) {
        visibleState = state
    }

    val cardDescription = when (visibleState) {
        is SleepPredictionState.Window ->
            "Next sleep around ${(visibleState as SleepPredictionState.Window).window.bestEstimate.formatTime()}. Open sleep screen."
        is SleepPredictionState.NeedMoreData ->
            "Sleep prediction: still learning patterns. Open sleep screen."
        SleepPredictionState.Overdue ->
            "Sleep prediction: watch for cues. Open sleep screen."
        SleepPredictionState.CurrentlySleeping ->
            "Baby is sleeping. Open sleep screen."
        SleepPredictionState.CueLed ->
            "Sleep prediction: cue-led. Open sleep screen."
        SleepPredictionState.AfterActiveFeed ->
            "Sleep prediction: window appears after feed. Open sleep screen."
        is SleepPredictionState.Unavailable ->
            "Sleep prediction. Open sleep screen."
    }

    AnimatedVisibility(
        visible = state !is SleepPredictionState.Unavailable,
        enter = fadeIn(tween(200, easing = EaseOutQuart)),
        exit = fadeOut(tween(150, easing = EaseOutQuart)),
        modifier = modifier,
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = cardDescription },
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SLEEP PREDICTION",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    AnimatedVisibility(
                        visible = visibleState is SleepPredictionState.Window,
                        enter = fadeIn(tween(150, easing = EaseOutQuart)),
                        exit = fadeOut(tween(120, easing = EaseOutQuart)),
                    ) {
                        val confidence = (visibleState as? SleepPredictionState.Window)
                            ?.window?.confidence ?: Confidence.LOW
                        ConfidenceDots(confidence = confidence)
                    }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = visibleState,
                    transitionSpec = {
                        fadeIn(tween(200, easing = EaseOutQuart)) togetherWith
                            fadeOut(tween(150, easing = EaseOutQuart))
                    },
                    contentKey = { it::class },
                    label = "SleepPredictionCardContent",
                ) { targetState ->
                    when (targetState) {
                        is SleepPredictionState.Window -> WindowCardContent(window = targetState.window)
                        is SleepPredictionState.NeedMoreData -> NeedMoreDataCardContent(progress = targetState.progress)
                        SleepPredictionState.Overdue -> OverdueCardContent()
                        SleepPredictionState.CueLed -> CueLedCardContent()
                        SleepPredictionState.CurrentlySleeping -> CurrentlySleepingCardContent()
                        SleepPredictionState.AfterActiveFeed -> AfterActiveFeedCardContent()
                        is SleepPredictionState.Unavailable -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowCardContent(window: SleepWindow) {
    var safetyExpanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Around ${window.bestEstimate.formatTime()}",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics {
                contentDescription = "Next sleep around ${window.bestEstimate.formatTime()}"
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    contentDescription = "Safe sleep tip"
                    stateDescription = if (safetyExpanded) "Expanded" else "Collapsed"
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
        AnimatedVisibility(
            visible = safetyExpanded,
            enter = fadeIn(tween(150, easing = EaseOutQuart)),
            exit = fadeOut(tween(120, easing = EaseOutQuart)),
        ) {
            Text(
                text = window.safetyPrompt,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
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
            val alpha by animateFloatAsState(
                targetValue = if (filled) 1f else 0.22f,
                animationSpec = tween(150, easing = EaseOutQuart),
                label = "confidence_dot_$i",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
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
                val alpha by animateFloatAsState(
                    targetValue = if (isFilled) 1f else 0.22f,
                    animationSpec = tween(200, easing = EaseOutQuart),
                    label = "progress_dot_$i",
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                            shape = CircleShape,
                        ),
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = "$filled of $total sleep sessions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (progress.localDays in 1 until progress.requiredLocalDays) {
            Text(
                text = "Day ${progress.localDays} of ${progress.requiredLocalDays}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    text: String,
    iconAlpha: Float = 1f,
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
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    )
}

@Composable
private fun AfterActiveFeedCardContent() {
    StatusRow(
        icon = Icons.Outlined.ChildCare,
        text = "Feeding now — sleep window appears after feed ends",
    )
}
