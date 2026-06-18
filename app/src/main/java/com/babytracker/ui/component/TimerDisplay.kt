package com.babytracker.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babytracker.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Displays a live elapsed-time counter.
 *
 * When [maxDurationSeconds] > 0 the timer is wrapped in a circular conic arc that
 * fills as time passes. The arc turns [MaterialTheme.colorScheme.tertiary] (green) once
 * the elapsed time exceeds [maxDurationSeconds]. When [maxDurationSeconds] == 0 only
 * the text clock is rendered (no ring).
 */
@Composable
fun TimerDisplay(
    startTimeMillis: Long,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    maxDurationSeconds: Int = 0,
    ringColor: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    frozenElapsedSeconds: Long? = null,
    elapsedFormatter: (Long) -> String = ::formatElapsedAsMinutesSeconds,
) {
    var elapsedSeconds by remember { mutableLongStateOf(frozenElapsedSeconds ?: 0L) }

    LaunchedEffect(isRunning, startTimeMillis, frozenElapsedSeconds) {
        if (isRunning) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
                delay(1000L)
            }
        } else if (frozenElapsedSeconds != null) {
            elapsedSeconds = frozenElapsedSeconds
        }
    }

    val timeText = elapsedFormatter(elapsedSeconds)
    val hasRing = maxDurationSeconds > 0

    val progress = if (hasRing) (elapsedSeconds.toFloat() / maxDurationSeconds).coerceIn(0f, 1f) else 0f
    val isOverMax = hasRing && elapsedSeconds >= maxDurationSeconds
    val resolvedRingColor = if (ringColor == Color.Unspecified) MaterialTheme.colorScheme.primary else ringColor
    val resolvedTrackColor = if (trackColor == Color.Unspecified) MaterialTheme.colorScheme.primaryContainer else trackColor
    val progressColor by animateColorAsState(
        targetValue = if (isOverMax) MaterialTheme.colorScheme.tertiary else resolvedRingColor,
        animationSpec = tween(durationMillis = 600),
        label = "ring_progress_color",
    )

    val scale = if (shouldAnimateTimerRing(hasRing, isRunning)) {
        val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ring_scale"
        )
        animatedScale
    } else {
        1f
    }

    val percent = if (hasRing) (progress * 100).toInt() else 0
    val maxMinutes = maxDurationSeconds / 60
    val baseDescription = if (hasRing) {
        stringResource(R.string.timer_cd_with_ring, timeText, percent, maxMinutes)
    } else {
        stringResource(R.string.timer_cd, timeText)
    }
    val timerDescription = if (!isRunning) {
        stringResource(R.string.timer_cd_paused, baseDescription)
    } else {
        baseDescription
    }

    Box(
        modifier = modifier
            .then(if (hasRing) Modifier.size(208.dp).scale(scale) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = timerDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (hasRing) {
            val strokeWidthDp = 16.dp
            Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val strokePx = strokeWidthDp.toPx()
                val diameter = size.minDimension - strokePx
                val topLeft = Offset(
                    x = (size.width - diameter) / 2,
                    y = (size.height - diameter) / 2
                )
                val arcSize = Size(diameter, diameter)
                val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

                drawArc(
                    color = resolvedTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                if (progress > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.timer_label_percent, percent, maxMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}

internal fun formatElapsedAsMinutesSeconds(elapsedSeconds: Long): String {
    val totalMinutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    return String.format(Locale.US, "%02d:%02d", totalMinutes, seconds)
}

internal fun formatElapsedAsClock(elapsedSeconds: Long): String {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds / 60) % 60
    val seconds = elapsedSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

internal fun shouldAnimateTimerRing(hasRing: Boolean, isRunning: Boolean): Boolean = hasRing && isRunning
