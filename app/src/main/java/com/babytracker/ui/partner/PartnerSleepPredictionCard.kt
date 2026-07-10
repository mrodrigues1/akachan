package com.babytracker.ui.partner

import android.content.Context
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.PredictionStateLabel
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.ui.home.EaseOutQuart
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatTime
import com.babytracker.util.resolve
import java.time.Duration
import java.time.Instant

private const val OVERDUE_GRACE_MINUTES = 45L
private const val MAX_REASONS = 2

private enum class PredictionVariant { WINDOW, OVERDUE, CURRENTLY_SLEEPING, AFTER_ACTIVE_FEED, NEED_MORE_DATA, CUE_LED }

/**
 * Read-only sleep prediction card for the partner dashboard. Renders a static snapshot computed on
 * the primary device. Hidden entirely (no layout space) when [prediction] is null. A synced WINDOW
 * whose end has passed by more than [OVERDUE_GRACE_MINUTES] is re-rendered as OVERDUE on this device.
 *
 * A synced CURRENTLY_SLEEPING is only trusted while the snapshot still carries an open sleep record
 * ([hasActiveSleep]); once the nap has ended the prediction is stale, so the "in progress" card is
 * hidden rather than claiming a nap that is no longer happening. AFTER_ACTIVE_FEED is gated the
 * same way on [hasActiveFeeding] so a feed that has since ended is not reported as "feeding now".
 */
@Composable
internal fun PartnerSleepPredictionCard(
    prediction: SleepPredictionSnapshot?,
    now: Instant,
    modifier: Modifier = Modifier,
    activeSleepType: SleepType? = null,
    hasActiveSleep: Boolean = false,
    hasActiveFeeding: Boolean = false,
) {
    if (prediction == null) return
    val variant = prediction.toVariant(now, hasActiveSleep, hasActiveFeeding) ?: return

    val context = LocalContext.current
    val estimatedAgo = Duration.between(Instant.ofEpochMilli(prediction.generatedAt), now)
        .coerceAtLeast(Duration.ZERO)
        .formatElapsedAgo(context)
        .lowercaseFirstChar()
    val estimatedLine = stringResource(R.string.partner_prediction_estimated, estimatedAgo)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = prediction.cardDescription(variant, estimatedLine, context)
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
            HeaderRow(label = variant.headerLabel(context), confidenceDots = variant.confidenceDots(prediction))
            Spacer(Modifier.height(12.dp))
            when (variant) {
                PredictionVariant.WINDOW -> WindowContent(prediction)
                PredictionVariant.OVERDUE -> TwoLineContent(
                    primary = stringResource(R.string.partner_prediction_overdue_primary),
                    secondary = stringResource(R.string.partner_prediction_overdue_secondary),
                )
                PredictionVariant.CURRENTLY_SLEEPING -> TwoLineContent(
                    primary = activeSleepType
                        ?.let { stringResource(R.string.partner_status_in_progress, sleepTypeLabel(it, context)) }
                        ?: stringResource(R.string.partner_prediction_sleeping_primary),
                    secondary = stringResource(R.string.partner_prediction_sleeping_secondary),
                )
                PredictionVariant.AFTER_ACTIVE_FEED -> SingleLineContent(
                    stringResource(R.string.partner_prediction_after_feed),
                )
                PredictionVariant.NEED_MORE_DATA -> SingleLineContent(
                    stringResource(R.string.partner_prediction_need_more_data),
                )
                PredictionVariant.CUE_LED -> SingleLineContent(
                    stringResource(R.string.partner_prediction_cue_led),
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
    val context = LocalContext.current
    val bestEstimate = prediction.bestEstimate?.let { Instant.ofEpochMilli(it).formatTime() }
    val range = rangeText(prediction, context)
    // Synced reasons are semantic; resolve them here so the text follows this device's locale.
    val reasons = prediction.reasons.take(MAX_REASONS).joinToString(" · ") { it.resolve(context) }

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
        if (prediction.feedDue) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sleep_feed_prompt),
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

private fun SleepPredictionSnapshot.toVariant(
    now: Instant,
    hasActiveSleep: Boolean,
    hasActiveFeeding: Boolean,
): PredictionVariant? =
    when (stateLabel) {
        PredictionStateLabel.WINDOW -> if (isStale(now)) PredictionVariant.OVERDUE else PredictionVariant.WINDOW
        PredictionStateLabel.OVERDUE -> PredictionVariant.OVERDUE
        PredictionStateLabel.CURRENTLY_SLEEPING ->
            if (hasActiveSleep) PredictionVariant.CURRENTLY_SLEEPING else null
        PredictionStateLabel.AFTER_ACTIVE_FEED ->
            if (hasActiveFeeding) PredictionVariant.AFTER_ACTIVE_FEED else null
        PredictionStateLabel.NEED_MORE_DATA -> PredictionVariant.NEED_MORE_DATA
        PredictionStateLabel.CUE_LED -> PredictionVariant.CUE_LED
        // UNAVAILABLE is never produced locally; it is the read-side fallback for a wire value this
        // build doesn't recognize. Hidden — same as the old string `when`'s accidental fall-through.
        PredictionStateLabel.UNAVAILABLE -> null
    }

private fun SleepPredictionSnapshot.isStale(now: Instant): Boolean {
    val end = windowEnd ?: return false
    val graceMs = Duration.ofMinutes(OVERDUE_GRACE_MINUTES).toMillis()
    return now.toEpochMilli() > end + graceMs
}

private fun PredictionVariant.headerLabel(context: Context): String =
    when (this) {
        PredictionVariant.WINDOW -> context.getString(R.string.partner_prediction_header_window)
        PredictionVariant.OVERDUE -> context.getString(R.string.partner_prediction_header_passed)
        PredictionVariant.CURRENTLY_SLEEPING -> context.getString(R.string.partner_prediction_header_sleeping)
        PredictionVariant.AFTER_ACTIVE_FEED -> context.getString(R.string.partner_prediction_header_feeding)
        PredictionVariant.NEED_MORE_DATA, PredictionVariant.CUE_LED ->
            context.getString(R.string.partner_prediction_header_generic)
    }

private fun confidenceLabel(confidence: String, context: Context): String =
    when (confidence) {
        "LOW" -> context.getString(R.string.partner_confidence_low)
        "MEDIUM" -> context.getString(R.string.partner_confidence_medium)
        "HIGH" -> context.getString(R.string.partner_confidence_high)
        else -> confidence.lowercase()
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

private fun rangeText(prediction: SleepPredictionSnapshot, context: Context): String? {
    val start = prediction.windowStart?.let { Instant.ofEpochMilli(it).formatTime() }
    val end = prediction.windowEnd?.let { Instant.ofEpochMilli(it).formatTime() }
    return if (start != null && end != null) {
        context.getString(R.string.partner_prediction_range, start, end)
    } else {
        null
    }
}

private fun SleepPredictionSnapshot.cardDescription(
    variant: PredictionVariant,
    estimatedLine: String,
    context: Context,
): String {
    val summary = when (variant) {
        PredictionVariant.WINDOW -> {
            val best = bestEstimate?.let { Instant.ofEpochMilli(it).formatTime() }
            val start = windowStart?.let { Instant.ofEpochMilli(it).formatTime() }
            val end = windowEnd?.let { Instant.ofEpochMilli(it).formatTime() }
            val confidenceText = confidence?.let { confidenceLabel(it, context) }
            buildString {
                append(context.getString(R.string.partner_prediction_cd_window))
                if (start != null && end != null) {
                    append(context.getString(R.string.partner_prediction_cd_window_range, start, end))
                }
                if (best != null) append(context.getString(R.string.partner_prediction_cd_best, best))
                if (confidenceText != null) {
                    append(context.getString(R.string.partner_prediction_cd_confidence, confidenceText))
                }
            }
        }
        PredictionVariant.OVERDUE -> context.getString(R.string.partner_prediction_cd_overdue)
        PredictionVariant.CURRENTLY_SLEEPING -> context.getString(R.string.partner_prediction_cd_sleeping)
        PredictionVariant.AFTER_ACTIVE_FEED -> context.getString(R.string.partner_prediction_cd_after_feed)
        PredictionVariant.NEED_MORE_DATA -> context.getString(R.string.partner_prediction_cd_need_more_data)
        PredictionVariant.CUE_LED -> context.getString(R.string.partner_prediction_cd_cue_led)
    }
    return context.getString(R.string.partner_prediction_cd, summary, estimatedLine)
}
