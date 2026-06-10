package com.babytracker.ui.partner

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatTime
import java.time.Duration
import java.time.Instant

private const val OVERDUE_GRACE_MINUTES = 45L
private const val MAX_REASONS = 2
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

private enum class PredictionVariant { WINDOW, OVERDUE, CURRENTLY_SLEEPING, AFTER_ACTIVE_FEED, NEED_MORE_DATA, CUE_LED }

/**
 * Read-only sleep prediction card for the partner dashboard. Renders a static snapshot computed on
 * the primary device. Hidden entirely (no layout space) when [prediction] is null. A synced WINDOW
 * whose end has passed by more than [OVERDUE_GRACE_MINUTES] is re-rendered as OVERDUE on this device.
 */
@Composable
internal fun PartnerSleepPredictionCard(
    prediction: SleepPredictionSnapshot?,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    if (prediction == null) return
    val variant = prediction.toVariant(now) ?: return

    val estimatedAgo = Duration.between(Instant.ofEpochMilli(prediction.generatedAt), now)
        .coerceAtLeast(Duration.ZERO)
        .formatElapsedAgo()
        .replaceFirstChar { it.lowercase() }
    val estimatedLine = "Estimated $estimatedAgo"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = prediction.cardDescription(variant, estimatedLine)
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            HeaderRow(label = variant.headerLabel(), confidenceDots = variant.confidenceDots(prediction))
            Spacer(Modifier.height(12.dp))
            when (variant) {
                PredictionVariant.WINDOW -> WindowContent(prediction)
                PredictionVariant.OVERDUE -> TwoLineContent(
                    primary = "Watch for sleepy cues",
                    secondary = "Next opportunity soon",
                )
                PredictionVariant.CURRENTLY_SLEEPING -> TwoLineContent(
                    primary = "Nap in progress",
                    secondary = "Next window appears after wake",
                )
                PredictionVariant.AFTER_ACTIVE_FEED -> SingleLineContent(
                    "Sleep window appears after this feed ends",
                )
                PredictionVariant.NEED_MORE_DATA -> SingleLineContent(
                    "Not enough logged data yet for a prediction",
                )
                PredictionVariant.CUE_LED -> SingleLineContent(
                    "Too early for predictions. Watch for cues",
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = estimatedLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun HeaderRow(label: String, confidenceDots: Int) {
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
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (confidenceDots > 0) {
            ConfidenceDots(filled = confidenceDots)
        }
    }
}

@Composable
private fun ConfidenceDots(filled: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        repeat(3) { index ->
            val alpha = if (index < filled) 1f else 0.22f
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
private fun WindowContent(prediction: SleepPredictionSnapshot) {
    val bestEstimate = prediction.bestEstimate?.let { Instant.ofEpochMilli(it).formatTime() }
    val range = rangeText(prediction)
    val reasons = prediction.reasons.take(MAX_REASONS).joinToString(" · ")

    Column {
        if (bestEstimate != null) {
            Text(
                text = bestEstimate,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (range != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = range,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f),
            )
        }
        if (reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = reasons,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        prediction.feedPrompt?.let { prompt ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TwoLineContent(primary: String, secondary: String) {
    Column {
        Text(
            text = primary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SingleLineContent(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

private fun SleepPredictionSnapshot.toVariant(now: Instant): PredictionVariant? =
    when (stateLabel) {
        "WINDOW" -> if (isStale(now)) PredictionVariant.OVERDUE else PredictionVariant.WINDOW
        "OVERDUE" -> PredictionVariant.OVERDUE
        "CURRENTLY_SLEEPING" -> PredictionVariant.CURRENTLY_SLEEPING
        "AFTER_ACTIVE_FEED" -> PredictionVariant.AFTER_ACTIVE_FEED
        "NEED_MORE_DATA" -> PredictionVariant.NEED_MORE_DATA
        "CUE_LED" -> PredictionVariant.CUE_LED
        else -> null
    }

private fun SleepPredictionSnapshot.isStale(now: Instant): Boolean {
    val end = windowEnd ?: return false
    val graceMs = Duration.ofMinutes(OVERDUE_GRACE_MINUTES).toMillis()
    return now.toEpochMilli() > end + graceMs
}

private fun PredictionVariant.headerLabel(): String =
    when (this) {
        PredictionVariant.WINDOW -> "NEXT SLEEP WINDOW"
        PredictionVariant.OVERDUE -> "SLEEP WINDOW PASSED"
        PredictionVariant.CURRENTLY_SLEEPING -> "SLEEPING"
        PredictionVariant.AFTER_ACTIVE_FEED -> "FEEDING NOW"
        PredictionVariant.NEED_MORE_DATA, PredictionVariant.CUE_LED -> "SLEEP PREDICTION"
    }

private fun PredictionVariant.confidenceDots(prediction: SleepPredictionSnapshot): Int =
    if (this == PredictionVariant.WINDOW) {
        when (prediction.confidence) {
            "LOW" -> 1
            "MEDIUM" -> 2
            "HIGH" -> 3
            else -> 0
        }
    } else {
        0
    }

private fun rangeText(prediction: SleepPredictionSnapshot): String? {
    val start = prediction.windowStart?.let { Instant.ofEpochMilli(it).formatTime() }
    val end = prediction.windowEnd?.let { Instant.ofEpochMilli(it).formatTime() }
    return if (start != null && end != null) "$start – $end" else null
}

private fun SleepPredictionSnapshot.cardDescription(
    variant: PredictionVariant,
    estimatedLine: String,
): String {
    val summary = when (variant) {
        PredictionVariant.WINDOW -> {
            val best = bestEstimate?.let { Instant.ofEpochMilli(it).formatTime() }
            val start = windowStart?.let { Instant.ofEpochMilli(it).formatTime() }
            val end = windowEnd?.let { Instant.ofEpochMilli(it).formatTime() }
            val confidenceText = confidence?.lowercase()
            buildString {
                append("next window")
                if (start != null && end != null) append(" $start to $end")
                if (best != null) append(", best estimate $best")
                if (confidenceText != null) append(", $confidenceText confidence")
            }
        }
        PredictionVariant.OVERDUE -> "sleep window passed, watch for sleepy cues"
        PredictionVariant.CURRENTLY_SLEEPING -> "baby is sleeping, next window appears after wake"
        PredictionVariant.AFTER_ACTIVE_FEED -> "feeding now, sleep window appears after this feed ends"
        PredictionVariant.NEED_MORE_DATA -> "not enough logged data yet for a prediction"
        PredictionVariant.CUE_LED -> "too early for predictions, watch for cues"
    }
    return "Sleep prediction: $summary. $estimatedLine."
}
