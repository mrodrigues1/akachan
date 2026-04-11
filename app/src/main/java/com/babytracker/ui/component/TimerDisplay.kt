package com.babytracker.ui.component

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    maxDurationSeconds: Int = 0,
    ringColor: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning, startTimeMillis) {
        if (isRunning) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
                delay(1000L)
            }
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val timeText = String.format(Locale.US, "%02d:%02d", hours, minutes)

    if (maxDurationSeconds <= 0) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.displaySmall,
            modifier = modifier
        )
        return
    }

    val progress = (elapsedSeconds.toFloat() / maxDurationSeconds).coerceIn(0f, 1f)
    val isOverMax = elapsedSeconds >= maxDurationSeconds

    val resolvedRingColor = if (ringColor == Color.Unspecified) MaterialTheme.colorScheme.primary else ringColor
    val resolvedTrackColor = if (trackColor == Color.Unspecified) MaterialTheme.colorScheme.primaryContainer else trackColor

    val progressColor = if (isOverMax) {
        MaterialTheme.colorScheme.tertiary
    } else {
        resolvedRingColor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
    val scale by if (isRunning) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ring_scale"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(208.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        val strokeWidthDp = 16.dp
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidthDp.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset(
                x = (size.width - diameter) / 2,
                y = (size.height - diameter) / 2
            )
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

            // Background track
            drawArc(
                color = resolvedTrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Progress arc
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
            val percent = (progress * 100).toInt()
            val maxMinutes = maxDurationSeconds / 60
            Text(
                text = "$percent% of ${maxMinutes}m",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
