package com.babytracker.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.babytracker.R
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.ui.theme.BottleFeedGreen
import com.babytracker.ui.theme.BottleFeedGreenDark
import com.babytracker.ui.theme.LocalDarkTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val ROW_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
private val DATE_LABEL_WIDTH = 44.dp
private val ROW_HEIGHT = 18.dp
private val ROW_SPACING = 4.dp
private val TRACK_CORNER = 4.dp
private const val FEED_DOT_RADIUS_DP = 2.5f
private const val FEED_DOT_RING_DP = 1f
private val RULER_TICKS = listOf("12a", "6a", "12p", "6p", "12a")

// Feed dots are tiny (2.5dp); widen the tap target so a finger can land one.
private val FEED_HIT_TOLERANCE = 16.dp
private val TOOLTIP_GAP = 6.dp
private const val MINUTES_PER_DAY = 1440
private val TOOLTIP_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

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
    val bottleColor = if (LocalDarkTheme.current) BottleFeedGreenDark else BottleFeedGreen
    val feedRing = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Type names resolved here (the tap lambda can't call composables) and reused by the legend.
    val breastLabel = stringResource(R.string.trends_legend_breast)
    val bottleLabel = stringResource(R.string.trends_legend_bottle)
    val nightLabel = stringResource(R.string.trends_legend_night)
    val napLabel = stringResource(R.string.trends_legend_nap)

    // Which element the parent last tapped; cleared when the range (data) changes.
    var selected by remember(data) { mutableStateOf<RhythmHit?>(null) }
    val gapPx = with(LocalDensity.current) { TOOLTIP_GAP.roundToPx() }

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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ROW_HEIGHT),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .pointerInput(day) {
                                detectTapGestures { offset ->
                                    selected = hitTest(
                                        day, offset, size.width, FEED_HIT_TOLERANCE.toPx(),
                                        breastLabel, bottleLabel, nightLabel, napLabel,
                                    )
                                }
                            },
                    ) {
                        drawDayRow(day, trackColor, nightColor, napColor, breastColor, bottleColor, feedRing)
                    }
                    val hit = selected
                    if (hit != null && hit.date == day.date) {
                        Popup(popupPositionProvider = RhythmTooltipPositionProvider(hit.tapX, gapPx)) {
                            RhythmTooltip(hit.label)
                        }
                    }
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

/** A tapped element: its day (to anchor the tooltip), the tap x in px, and the time + type label. */
private data class RhythmHit(val date: LocalDate, val tapX: Int, val label: String)

/**
 * Maps a tap on a day row to the element under the finger: the nearest feed dot within
 * [tolerancePx], else the sleep block containing the tap, else null (a tap on empty track, which
 * dismisses any open tooltip). Returns the "type · time" label to show.
 */
private fun hitTest(
    day: DayRhythm,
    offset: Offset,
    width: Int,
    tolerancePx: Float,
    breastLabel: String,
    bottleLabel: String,
    nightLabel: String,
    napLabel: String,
): RhythmHit? {
    if (width <= 0) return null
    val fraction = (offset.x / width).coerceIn(0f, 1f)
    val feeds = day.breastFeedMarks.map { it to breastLabel } + day.bottleFeedMarks.map { it to bottleLabel }
    val nearestFeed = feeds.minByOrNull { abs(it.first - fraction) }
    val label = if (nearestFeed != null && abs(nearestFeed.first - fraction) * width <= tolerancePx) {
        "${nearestFeed.second} · ${fractionToTime(nearestFeed.first)}"
    } else {
        day.sleepBlocks.firstOrNull { fraction in it.startFraction..it.endFraction }?.let { block ->
            val type = if (block.isNight) nightLabel else napLabel
            "$type · ${fractionToTime(block.startFraction)}–${fractionToTime(block.endFraction)}"
        }
    }
    return label?.let { RhythmHit(day.date, offset.x.roundToInt(), it) }
}

/** A day fraction in [0, 1] as a wall-clock time; a 1.0 end (midnight) wraps to 12:00 AM. */
private fun fractionToTime(fraction: Float): String {
    val minuteOfDay = (fraction * MINUTES_PER_DAY).roundToInt().coerceIn(0, MINUTES_PER_DAY) % MINUTES_PER_DAY
    return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(TOOLTIP_TIME_FORMAT)
}

/** The value pill: matches the Vico chart marker — surface pill, outline stroke, centered above the tap. */
@Composable
private fun RhythmTooltip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Centers the tooltip horizontally on the tap and floats it [gapPx] above the row, clamped on-screen. */
private class RhythmTooltipPositionProvider(
    private val tapX: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + tapX - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
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
