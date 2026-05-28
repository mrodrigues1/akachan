package com.babytracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatElapsedShort
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import java.time.Duration
import java.time.Instant

private const val FEED_EMOJI = "🍼"
private const val SLEEP_EMOJI = "🌙"

private val SURFACE_PADDING = 8.dp
private val CHIP_GAP = 6.dp
private val ROW_GAP = 8.dp
private val BADGE_SIZE = 36.dp
private val BADGE_GAP = 10.dp

/**
 * Small (2×1): two domain chips side by side, each filling the widget height as its own
 * tap target. Feed chip routes to Breastfeeding, sleep chip routes to Sleep. Elapsed text
 * stays current via the 15-minute [WidgetRefreshWorker].
 */
@Composable
fun SmallContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(SURFACE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DomainChip(
            badgeRes = R.drawable.widget_feed_badge,
            emoji = FEED_EMOJI,
            label = feedChipLabel(data.lastFeedSide, data.lastFeedStart, now),
            textColor = GlanceTheme.colors.onPrimaryContainer,
            onClick = openBreastfeedingAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
        Spacer(modifier = GlanceModifier.width(CHIP_GAP))
        DomainChip(
            badgeRes = R.drawable.widget_sleep_badge,
            emoji = SLEEP_EMOJI,
            label = sleepChipLabel(data.sleepState, data.sleepSince, now),
            textColor = GlanceTheme.colors.onSecondaryContainer,
            onClick = openSleepAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
}

/**
 * Medium (2×2): a quiet baby-name header over two domain rows. Each row leads with a
 * container-tinted badge, then a title line and an elapsed value in the domain's action
 * color. Header → Home, feed row → Breastfeeding, sleep row → Sleep.
 */
@Composable
fun MediumContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(12.dp),
    ) {
        Text(
            text = data.babyName,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            modifier = GlanceModifier.fillMaxWidth().clickable(openHomeAction()),
        )
        Spacer(modifier = GlanceModifier.height(ROW_GAP))
        DomainRow(
            badgeRes = R.drawable.widget_feed_badge,
            emoji = FEED_EMOJI,
            title = feedTitle(data.lastFeedSide),
            value = feedValue(data.lastFeedStart, now),
            valueColor = GlanceTheme.colors.primary,
            onClick = openBreastfeedingAction(),
        )
        Spacer(modifier = GlanceModifier.height(ROW_GAP))
        DomainRow(
            badgeRes = R.drawable.widget_sleep_badge,
            emoji = SLEEP_EMOJI,
            title = sleepTitle(data.sleepState),
            value = sleepValue(data.sleepState, data.sleepSince, now),
            valueColor = GlanceTheme.colors.secondary,
            onClick = openSleepAction(),
        )
    }
}

@Composable
private fun DomainChip(
    badgeRes: Int,
    emoji: String,
    label: String,
    textColor: ColorProvider,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier
            .background(ImageProvider(badgeRes))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, style = TextStyle(fontSize = 13.sp))
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun DomainRow(
    badgeRes: Int,
    emoji: String,
    title: String,
    value: String?,
    valueColor: ColorProvider,
    onClick: Action,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(BADGE_SIZE).background(ImageProvider(badgeRes)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = TextStyle(fontSize = 18.sp))
        }
        Spacer(modifier = GlanceModifier.width(BADGE_GAP))
        Column {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
            )
            if (value != null) {
                Text(
                    text = value,
                    maxLines = 1,
                    style = TextStyle(color = valueColor, fontSize = 12.sp),
                )
            }
        }
    }
}

internal fun BreastSide.label(): String = when (this) {
    BreastSide.LEFT -> "Left"
    BreastSide.RIGHT -> "Right"
}

// --- Pure label builders (unit-tested in WidgetContentHelpersTest) ---

internal fun feedChipLabel(side: BreastSide?, start: Instant?, now: Instant): String =
    if (side == null || start == null) {
        "No feeds yet"
    } else {
        "${side.label()} · ${Duration.between(start, now).formatElapsedShort()}"
    }

internal fun sleepChipLabel(state: SleepState, since: Instant?, now: Instant): String =
    when (state) {
        SleepState.NONE -> "No sleep yet"
        SleepState.SLEEPING ->
            since?.let { "Sleeping ${Duration.between(it, now).formatElapsedShort()}" } ?: "Sleeping"
        SleepState.AWAKE ->
            since?.let { "Awake ${Duration.between(it, now).formatElapsedShort()}" } ?: "Awake"
    }

internal fun feedTitle(side: BreastSide?): String = side?.label() ?: "No feeds yet"

internal fun feedValue(start: Instant?, now: Instant): String? =
    start?.let { Duration.between(it, now).formatElapsedAgo() }

internal fun sleepTitle(state: SleepState): String = when (state) {
    SleepState.NONE -> "No sleep logged"
    SleepState.SLEEPING -> "Sleeping"
    SleepState.AWAKE -> "Awake"
}

internal fun sleepValue(state: SleepState, since: Instant?, now: Instant): String? =
    when (state) {
        SleepState.NONE -> null
        SleepState.SLEEPING -> since?.let { Duration.between(it, now).formatElapsedShort() }
        SleepState.AWAKE -> since?.let { Duration.between(it, now).formatElapsedAgo() }
    }
