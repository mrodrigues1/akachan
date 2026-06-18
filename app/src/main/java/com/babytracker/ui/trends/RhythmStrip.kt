package com.babytracker.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.ui.theme.BottleFeedRed
import com.babytracker.ui.theme.BottleFeedRedDark
import com.babytracker.ui.theme.LocalDarkTheme
import java.time.format.DateTimeFormatter

private val ROW_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
private val DATE_LABEL_WIDTH = 44.dp
private val ROW_HEIGHT = 18.dp
private val ROW_SPACING = 4.dp
private val TRACK_CORNER = 4.dp
private const val FEED_DOT_RADIUS_DP = 2.5f
private const val FEED_DOT_RING_DP = 1f
private val RULER_TICKS = listOf("12a", "6a", "12p", "6p", "12a")

/**
 * Per-day 24h timeline: each row is one day from midnight to midnight, with sleep drawn as bars
 * (night vs nap) and feeds as dots. Reads fractions from [DayRhythm]; all drawing colors are
 * captured outside the [DrawScope] since it cannot call composables.
 */
@Composable
fun RhythmStrip(
    data: List<DayRhythm>,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val nightColor = MaterialTheme.colorScheme.secondary
    val napColor = MaterialTheme.colorScheme.secondaryContainer
    val breastColor = MaterialTheme.colorScheme.primary
    val bottleColor = if (LocalDarkTheme.current) BottleFeedRedDark else BottleFeedRed
    val feedRing = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(DATE_LABEL_WIDTH))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RULER_TICKS.forEach { tick ->
                    Text(tick, style = labelStyle, color = labelColor)
                }
            }
        }

        data.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ROW_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.date.format(ROW_DATE_FORMAT),
                    style = labelStyle,
                    color = labelColor,
                    modifier = Modifier.width(DATE_LABEL_WIDTH),
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(ROW_HEIGHT)
                        .clip(CircleShape),
                ) {
                    drawDayRow(day, trackColor, nightColor, napColor, breastColor, bottleColor, feedRing)
                }
            }
        }

        RhythmLegend(nightColor, napColor, breastColor, bottleColor, labelStyle, labelColor)
    }
}

private fun DrawScope.drawDayRow(
    day: DayRhythm,
    trackColor: Color,
    nightColor: Color,
    napColor: Color,
    breastColor: Color,
    bottleColor: Color,
    feedRing: Color,
) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(TRACK_CORNER.toPx(), TRACK_CORNER.toPx())

    drawRoundRect(color = trackColor, cornerRadius = corner)

    day.sleepBlocks.forEach { block ->
        val left = block.startFraction * w
        val right = block.endFraction * w
        val barWidth = (right - left).coerceAtLeast(1f)
        drawRoundRect(
            color = if (block.isNight) nightColor else napColor,
            topLeft = Offset(left, 0f),
            size = Size(barWidth, h),
            cornerRadius = corner,
        )
    }

    val dotRadius = FEED_DOT_RADIUS_DP.dp.toPx()
    val ringRadius = dotRadius + FEED_DOT_RING_DP.dp.toPx()
    drawFeedDots(day.breastFeedMarks, breastColor, feedRing, dotRadius, ringRadius)
    drawFeedDots(day.bottleFeedMarks, bottleColor, feedRing, dotRadius, ringRadius)
}

private fun DrawScope.drawFeedDots(
    marks: List<Float>,
    dotColor: Color,
    ringColor: Color,
    dotRadius: Float,
    ringRadius: Float,
) {
    val w = size.width
    val h = size.height
    marks.forEach { mark ->
        val center = Offset(x = (mark * w).coerceIn(dotRadius, w - dotRadius), y = h / 2f)
        drawCircle(color = ringColor, radius = ringRadius, center = center)
        drawCircle(color = dotColor, radius = dotRadius, center = center)
    }
}

@Composable
private fun RhythmLegend(
    nightColor: Color,
    napColor: Color,
    breastColor: Color,
    bottleColor: Color,
    labelStyle: TextStyle,
    labelColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(stringResource(R.string.trends_legend_night), nightColor, labelStyle, labelColor)
        LegendSwatch(stringResource(R.string.trends_legend_nap), napColor, labelStyle, labelColor)
        LegendSwatch(stringResource(R.string.trends_legend_breast), breastColor, labelStyle, labelColor)
        LegendSwatch(stringResource(R.string.trends_legend_bottle), bottleColor, labelStyle, labelColor)
    }
}

@Composable
private fun LegendSwatch(
    label: String,
    color: Color,
    labelStyle: TextStyle,
    labelColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(10.dp).clip(CircleShape)) {
            drawCircle(color)
        }
        Text(label, style = labelStyle, color = labelColor)
    }
}
