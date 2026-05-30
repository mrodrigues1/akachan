package com.babytracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.util.formatElapsedShort
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import java.time.Duration
import java.time.Instant

private const val FEED_EMOJI = "🍼"
private const val SLEEP_EMOJI = "🌙"

/** Text scale presets for a [DomainBlock], so each size variant tunes the hero hierarchy in one place. */
private data class BlockTextSizes(val emoji: TextUnit, val label: TextUnit, val value: TextUnit)

private val MediumBlockSizes = BlockTextSizes(emoji = 18.sp, label = 11.sp, value = 20.sp)

/** What a [DomainBlock] shows: the visible emoji/label/value plus the merged screen-reader phrase. */
private data class DomainBlockContent(
    val emoji: String,
    val label: String,
    val value: String?,
    val description: String,
)

/**
 * Widget body: a quiet baby-name header over two full-width domain tiles. Each tile leads
 * with its emoji, a small label, and the elapsed value rendered large + Bold as the hero.
 * The sleep tile fills solid Sleep Blue while the baby is asleep now. Header → Home, feed
 * tile → Breastfeeding, sleep tile → Sleep.
 */
@Composable
fun MediumContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
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
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.fillMaxWidth().clickable(openHomeAction()),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) {
                GlanceTheme.colors.onPrimary
            } else {
                GlanceTheme.colors.onPrimaryContainer
            },
            sizes = MediumBlockSizes,
            onClick = openBreastfeedingAction(),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = MediumBlockSizes,
            onClick = openSleepAction(),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

@Composable
private fun DomainBlock(
    backgroundRes: Int,
    content: DomainBlockContent,
    contentColor: ColorProvider,
    sizes: BlockTextSizes,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    val surface = modifier
        .background(ImageProvider(backgroundRes))
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clickable(onClick)
        .semantics { contentDescription = content.description }
    Row(modifier = surface, verticalAlignment = Alignment.CenterVertically) {
        Text(text = content.emoji, style = TextStyle(fontSize = sizes.emoji))
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column {
            BlockLabel(content.label, contentColor, sizes.label)
            content.value?.let { BlockValue(it, contentColor, sizes.value) }
        }
    }
}

@Composable
private fun BlockLabel(text: String, color: ColorProvider, size: TextUnit) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun BlockValue(text: String, color: ColorProvider, size: TextUnit) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold),
    )
}

private fun feedBlockContent(data: WidgetData, now: Instant) = DomainBlockContent(
    emoji = FEED_EMOJI,
    label = feedLabel(data.lastFeedSide, data.feedState),
    value = feedValue(data.lastFeedStart, data.feedState, now),
    description = feedContentDescription(data.lastFeedSide, data.feedState, data.lastFeedStart, now),
)

private fun sleepBlockContent(data: WidgetData, now: Instant) = DomainBlockContent(
    emoji = SLEEP_EMOJI,
    label = sleepLabel(data.sleepState),
    value = sleepValue(data.sleepState, data.sleepSince, now),
    description = sleepContentDescription(data.sleepState, data.sleepSince, now),
)

private fun WidgetData.hasActiveFeed(): Boolean = feedState == FeedState.ACTIVE || feedState == FeedState.PAUSED

internal fun BreastSide.label(): String = when (this) {
    BreastSide.LEFT -> "Left"
    BreastSide.RIGHT -> "Right"
}

// --- Pure label/value builders, shared by both sizes (unit-tested in WidgetContentHelpersTest) ---

internal fun feedLabel(side: BreastSide?, state: FeedState): String =
    when (state) {
        FeedState.ACTIVE -> side?.let { "Feeding: ${it.label()}" } ?: "Feeding"
        FeedState.PAUSED -> side?.let { "Paused: ${it.label()}" } ?: "Paused"
        FeedState.RECENT -> side?.let { "Last: ${it.label()}" } ?: "Last feed"
        FeedState.NONE -> "No feeds yet"
    }

internal fun feedValue(start: Instant?, state: FeedState, now: Instant): String? =
    start?.let {
        val elapsed = Duration.between(it, now).formatElapsedShort()
        if (state == FeedState.RECENT) "$elapsed ago" else elapsed
    }

internal fun sleepLabel(state: SleepState): String = when (state) {
    SleepState.SLEEPING -> "Sleeping"
    SleepState.AWAKE -> "Awake"
    SleepState.NONE -> "No sleep yet"
}

internal fun sleepValue(state: SleepState, since: Instant?, now: Instant): String? =
    when (state) {
        SleepState.NONE -> null
        SleepState.SLEEPING, SleepState.AWAKE -> since?.let { Duration.between(it, now).formatElapsedShort() }
    }

// Screen-reader copy: each tile merges emoji + label + value into one spoken phrase with the
// context the loose nodes lack ("1h 20m" alone never says "1h 20m of what").

internal fun feedContentDescription(side: BreastSide?, state: FeedState, start: Instant?, now: Instant): String {
    if (side == null || start == null) return "No feeds yet"
    val elapsed = Duration.between(start, now).formatElapsedShort()
    return when (state) {
        FeedState.ACTIVE -> "Feeding active: ${side.label()} side, started $elapsed ago"
        FeedState.PAUSED -> "Feeding paused: ${side.label()} side, started $elapsed ago"
        FeedState.RECENT -> "Last feeding started $elapsed ago; last side used ${side.label()}"
        FeedState.NONE -> "No feeds yet"
    }
}

internal fun sleepContentDescription(state: SleepState, since: Instant?, now: Instant): String =
    when (state) {
        SleepState.NONE -> "No sleep yet"
        SleepState.SLEEPING -> since?.let { "Sleeping for ${Duration.between(it, now).formatElapsedShort()}" } ?: "Sleeping"
        SleepState.AWAKE -> since?.let { "Awake for ${Duration.between(it, now).formatElapsedShort()}" } ?: "Awake"
    }
