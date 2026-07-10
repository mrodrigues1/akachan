package com.babytracker.ui.sleep

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.util.resolve

/**
 * Shared building blocks for [SleepPredictionCard] (home) and [SleepRecommendationSection] (sleep
 * screen), which render the same [com.babytracker.domain.model.SleepPredictionState] with different
 * card chrome. Only the two callers' Window content and outer Card/AnimatedVisibility scaffolding
 * differ; everything here was previously character-for-character duplicated between them.
 */

/** Sole definition after consolidating three copies (this file, the former ui/home one, and the
 * former ui/sleep ones) — Home and the partner dashboard's mirrored card import this one. */
internal val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@Composable
internal fun confidenceLabel(confidence: Confidence): String = when (confidence) {
    Confidence.LOW -> stringResource(R.string.sleep_prediction_confidence_low)
    Confidence.MEDIUM -> stringResource(R.string.sleep_prediction_confidence_medium)
    Confidence.HIGH -> stringResource(R.string.sleep_prediction_confidence_high)
}

@Composable
internal fun NeedMoreDataContent(progress: EvidenceProgress) {
    val total = progress.requiredIntervals.coerceAtMost(7)
    val filled = progress.completedIntervals.coerceAtMost(total)
    val a11yLabel = stringResource(R.string.sleep_prediction_a11y_progress, filled, total)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = progress.hint.resolve(LocalContext.current),
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
internal fun StatusRow(
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
internal fun OverdueContent() {
    StatusRow(
        icon = Icons.Outlined.HourglassEmpty,
        text = stringResource(R.string.sleep_prediction_overdue),
    )
}

@Composable
internal fun CueLedContent() {
    StatusRow(
        icon = Icons.Outlined.Visibility,
        text = stringResource(R.string.sleep_prediction_cue_led),
    )
}

@Composable
internal fun CurrentlySleepingContent() {
    StatusRow(
        icon = Icons.Outlined.Bedtime,
        text = stringResource(R.string.sleep_prediction_sleeping),
        iconAlpha = 0.7f,
    )
}

@Composable
internal fun AfterActiveFeedContent() {
    StatusRow(
        icon = Icons.Outlined.ChildCare,
        text = stringResource(R.string.sleep_prediction_after_feed),
    )
}
