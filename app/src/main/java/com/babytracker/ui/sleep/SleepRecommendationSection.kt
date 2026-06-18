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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime
import com.babytracker.util.resolve

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@Composable
internal fun SleepRecommendationSection(
    state: SleepPredictionState,
    modifier: Modifier = Modifier,
) {
    var visibleState by remember { mutableStateOf(state) }
    if (state !is SleepPredictionState.Unavailable) {
        visibleState = state
    }

    AnimatedVisibility(
        visible = state !is SleepPredictionState.Unavailable,
        enter = fadeIn(tween(200, easing = EaseOutQuart)),
        exit = fadeOut(tween(150, easing = EaseOutQuart)),
        modifier = modifier,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sleep_prediction_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = visibleState,
                    transitionSpec = {
                        fadeIn(tween(200, easing = EaseOutQuart)) togetherWith
                            fadeOut(tween(150, easing = EaseOutQuart))
                    },
                    contentKey = { it::class },
                    label = "SleepRecommendationSectionContent",
                ) { targetState ->
                    when (targetState) {
                        is SleepPredictionState.Window -> WindowSectionContent(window = targetState.window)
                        is SleepPredictionState.NeedMoreData -> NeedMoreDataSectionContent(progress = targetState.progress)
                        SleepPredictionState.Overdue -> OverdueSectionContent()
                        SleepPredictionState.CueLed -> CueLedSectionContent()
                        SleepPredictionState.CurrentlySleeping -> CurrentlySleepingSectionContent()
                        SleepPredictionState.AfterActiveFeed -> AfterActiveFeedSectionContent()
                        is SleepPredictionState.Unavailable -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowSectionContent(window: SleepWindow) {
    var safetyExpanded by remember { mutableStateOf(false) }
    val confidenceLabel = when (window.confidence) {
        Confidence.LOW -> stringResource(R.string.sleep_prediction_confidence_low)
        Confidence.MEDIUM -> stringResource(R.string.sleep_prediction_confidence_medium)
        Confidence.HIGH -> stringResource(R.string.sleep_prediction_confidence_high)
    }
    val aroundContentDescription =
        stringResource(R.string.sleep_prediction_cd_around, window.bestEstimate.formatTime())
    val safeSleepContentDescription = stringResource(R.string.sleep_prediction_safe_sleep_cd)
    val expandedStateDescription = stringResource(R.string.state_expanded)
    val collapsedStateDescription = stringResource(R.string.state_collapsed)

    Column {
        Text(
            text = stringResource(R.string.sleep_prediction_around, window.bestEstimate.formatTime()),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics {
                contentDescription = aroundContentDescription
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.sleep_prediction_window_range, window.windowStart.formatTime(), window.windowEnd.formatTime()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = confidenceLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
        if (window.reasons.isNotEmpty()) {
            val context = LocalContext.current
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
                        text = reason.resolve(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (window.feedDue) {
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
                    text = stringResource(R.string.sleep_feed_prompt),
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
                    contentDescription = safeSleepContentDescription
                    stateDescription = if (safetyExpanded) expandedStateDescription else collapsedStateDescription
                },
        ) {
            Text(
                text = stringResource(R.string.sleep_prediction_safe_sleep),
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
                text = stringResource(R.string.sleep_safety_prompt),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun NeedMoreDataSectionContent(progress: EvidenceProgress) {
    val total = progress.requiredIntervals.coerceAtMost(7)
    val filled = progress.completedIntervals.coerceAtMost(total)
    val a11yLabel = stringResource(R.string.sleep_prediction_a11y_progress, filled, total)

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
                text = stringResource(R.string.sleep_prediction_sessions, filled, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (progress.localDays in 1 until progress.requiredLocalDays) {
            Text(
                text = stringResource(R.string.sleep_prediction_day_of, progress.localDays, progress.requiredLocalDays),
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
private fun OverdueSectionContent() {
    StatusRow(
        icon = Icons.Outlined.HourglassEmpty,
        text = stringResource(R.string.sleep_prediction_overdue),
    )
}

@Composable
private fun CueLedSectionContent() {
    StatusRow(
        icon = Icons.Outlined.Visibility,
        text = stringResource(R.string.sleep_prediction_cue_led),
    )
}

@Composable
private fun CurrentlySleepingSectionContent() {
    StatusRow(
        icon = Icons.Outlined.Bedtime,
        text = stringResource(R.string.sleep_prediction_sleeping),
        iconAlpha = 0.7f,
    )
}

@Composable
private fun AfterActiveFeedSectionContent() {
    StatusRow(
        icon = Icons.Outlined.ChildCare,
        text = stringResource(R.string.sleep_prediction_after_feed),
    )
}
