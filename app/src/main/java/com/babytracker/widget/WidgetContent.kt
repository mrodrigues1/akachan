package com.babytracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
private data class BlockTextSizes(
    val emoji: TextUnit,
    val label: TextUnit,
    val value: TextUnit,
    val supporting: TextUnit,
)

private val NarrowBlockSizes = BlockTextSizes(emoji = 14.sp, label = 10.sp, value = 12.sp, supporting = 9.sp)
private val SmallBlockSizes = BlockTextSizes(emoji = 14.sp, label = 10.sp, value = 16.sp, supporting = 9.sp)
private val MediumBlockSizes = BlockTextSizes(emoji = 18.sp, label = 11.sp, value = 20.sp, supporting = 10.sp)
private val ThreeByThreeBlockSizes = BlockTextSizes(emoji = 20.sp, label = 12.sp, value = 22.sp, supporting = 11.sp)
private val FourByTwoBlockSizes = BlockTextSizes(emoji = 18.sp, label = 11.sp, value = 22.sp, supporting = 10.sp)
private val FourByThreeBlockSizes = BlockTextSizes(emoji = 22.sp, label = 12.sp, value = 26.sp, supporting = 11.sp)
private val FourByFourBlockSizes = BlockTextSizes(emoji = 22.sp, label = 12.sp, value = 28.sp, supporting = 11.sp)

/** What a [DomainBlock] shows: the visible emoji/label/value plus the merged screen-reader phrase. */
private data class DomainBlockContent(
    val emoji: String,
    val label: String,
    val value: String?,
    val supporting: String?,
    val description: String,
)

/**
 * Legal narrow-short launcher bounds cannot fit the medium stack. Keep both domains tappable
 * with compact labels and screen-reader descriptions instead of clipping the 2x2 layout.
 */
@Composable
fun SmallNarrowContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!shouldShowFeedRow(data) && !shouldShowSleepRow(data)) {
            BabyNameFallback(data.babyName, 12.sp)
        } else {
            NarrowDomainBlocks(data, now)
        }
    }
}

/**
 * Compact wide widget: two side-by-side domain tiles for one-row launchers. The elapsed
 * value stays as the glance hero while both feed and sleep remain visible.
 */
@Composable
fun SmallContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!shouldShowFeedRow(data) && !shouldShowSleepRow(data)) {
            BabyNameFallback(data.babyName, 14.sp)
        } else {
            StackedDomainBlocks(data, now, SmallBlockSizes, spacing = 8.dp, showSupporting = false)
        }
    }
}

/**
 * Medium widget: a quiet baby-name header over two full-width domain tiles. Each tile leads
 * with its emoji, a small label, and the elapsed value rendered large + Bold as the hero.
 * The sleep tile fills solid Sleep Blue while the baby is asleep now. Header → Home, feed
 * tile → Breastfeeding, sleep tile → Sleep.
 */
@Composable
fun MediumContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            )
            RefreshButton(isRefreshing)
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        FullWidthDomainBlocks(data, now, MediumBlockSizes, spacing = 8.dp)
    }
}

@Composable
fun TwoByFourContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            )
            RefreshButton(isRefreshing)
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        FullWidthDomainBlocks(data, now, MediumBlockSizes, spacing = 10.dp)
    }
}

@Composable
fun ThreeByThreeContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            )
            RefreshButton(isRefreshing)
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        FullWidthDomainBlocks(data, now, ThreeByThreeBlockSizes, spacing = 10.dp)
    }
}

@Composable
fun ThreeByFourContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            )
            RefreshButton(isRefreshing)
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        FullWidthDomainBlocks(data, now, ThreeByThreeBlockSizes, spacing = 10.dp)
    }
}

@Composable
fun FourByTwoContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WidgetHeader(data = data, status = widgetStatusSummary(data, now), titleSize = 13.sp, statusSize = 10.sp, isRefreshing = isRefreshing)
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StackedDomainBlocks(data, now, FourByTwoBlockSizes, spacing = 10.dp, showSupporting = true)
        }
    }
}

@Composable
fun FourByThreeContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            )
            RefreshButton(isRefreshing)
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StackedDomainBlocks(data, now, FourByThreeBlockSizes, spacing = 12.dp, showSupporting = false)
        }
    }
}

@Composable
fun FourByFourContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier, isRefreshing: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(16.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            )
            RefreshButton(isRefreshing)
        }
        Spacer(modifier = GlanceModifier.height(12.dp))
        FullWidthDomainBlocks(data, now, FourByFourBlockSizes, spacing = 12.dp)
    }
}

/**
 * Emits the feed and sleep tiles as full-width stacked blocks for the vertical layouts, each gated
 * on its feature flag. The interior spacer is dropped when only one tile shows, and both tiles
 * being hidden simply emits nothing (the caller keeps its baby-name header).
 */
@Composable
private fun ColumnScope.FullWidthDomainBlocks(data: WidgetData, now: Instant, sizes: BlockTextSizes, spacing: Dp) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    if (shouldShowFeedRow(data)) {
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onPrimaryContainer,
            sizes = sizes,
            onClick = openBreastfeedingAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
    if (shouldShowFeedRow(data) && shouldShowSleepRow(data)) {
        Spacer(modifier = GlanceModifier.height(spacing))
    }
    if (shouldShowSleepRow(data)) {
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = sizes,
            onClick = openSleepAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

/** Side-by-side stacked tiles for the horizontal layouts, each gated on its feature flag. */
@Composable
private fun RowScope.StackedDomainBlocks(data: WidgetData, now: Instant, sizes: BlockTextSizes, spacing: Dp, showSupporting: Boolean) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    if (shouldShowFeedRow(data)) {
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onPrimaryContainer,
            sizes = sizes,
            onClick = openBreastfeedingAction(),
            stacked = true,
            showSupporting = showSupporting,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
    if (shouldShowFeedRow(data) && shouldShowSleepRow(data)) {
        Spacer(modifier = GlanceModifier.width(spacing))
    }
    if (shouldShowSleepRow(data)) {
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = sizes,
            onClick = openSleepAction(),
            stacked = true,
            showSupporting = showSupporting,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
}

/** Side-by-side compact tiles for the smallest layout, each gated on its feature flag. */
@Composable
private fun RowScope.NarrowDomainBlocks(data: WidgetData, now: Instant) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    if (shouldShowFeedRow(data)) {
        NarrowDomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            label = "Feed",
            contentColor = if (feedingActive) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onPrimaryContainer,
            onClick = openBreastfeedingAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
    if (shouldShowFeedRow(data) && shouldShowSleepRow(data)) {
        Spacer(modifier = GlanceModifier.width(4.dp))
    }
    if (shouldShowSleepRow(data)) {
        NarrowDomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            label = "Sleep",
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            onClick = openSleepAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
}

@Composable
private fun WidgetHeader(
    data: WidgetData,
    status: String,
    titleSize: TextUnit,
    statusSize: TextUnit,
    isRefreshing: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().clickable(openHomeAction()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.babyName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = status,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = statusSize,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        RefreshButton(isRefreshing)
    }
}

@Composable
private fun RefreshButton(isRefreshing: Boolean) {
    if (isRefreshing) {
        Row(
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.widget_feed_badge))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .semantics { contentDescription = "Updating widget" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = GlanceModifier.width(14.dp).height(14.dp),
            )
            Spacer(modifier = GlanceModifier.width(5.dp))
            Text(
                text = "Updating",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                ),
            )
        }
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "Refresh widget",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier
                .width(20.dp)
                .height(20.dp)
                .clickable(refreshAction()),
        )
    }
}

@Composable
private fun NarrowDomainBlock(
    backgroundRes: Int,
    content: DomainBlockContent,
    label: String,
    contentColor: ColorProvider,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier
            .background(ImageProvider(backgroundRes))
            .padding(4.dp)
            .clickable(onClick)
            .semantics { contentDescription = content.description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = content.emoji, style = TextStyle(fontSize = NarrowBlockSizes.emoji))
            Spacer(modifier = GlanceModifier.width(4.dp))
            BlockLabel(label, contentColor, NarrowBlockSizes.label)
        }
        content.value?.let { BlockValue(it, contentColor, NarrowBlockSizes.value) }
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
    stacked: Boolean = false,
    showSupporting: Boolean = false,
) {
    val surface = modifier
        .background(ImageProvider(backgroundRes))
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clickable(onClick)
        .semantics { contentDescription = content.description }
    if (stacked) {
        Column(modifier = surface, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = content.emoji, style = TextStyle(fontSize = sizes.emoji))
                Spacer(modifier = GlanceModifier.width(8.dp))
                BlockLabel(content.label, contentColor, sizes.label)
            }
            content.value?.let { BlockValue(it, contentColor, sizes.value) }
            if (showSupporting) {
                content.supporting?.let { BlockSupporting(it, contentColor, sizes.supporting) }
            }
        }
    } else {
        Row(modifier = surface, verticalAlignment = Alignment.CenterVertically) {
            Text(text = content.emoji, style = TextStyle(fontSize = sizes.emoji))
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column {
                BlockLabel(content.label, contentColor, sizes.label)
                content.value?.let { BlockValue(it, contentColor, sizes.value) }
                if (showSupporting) {
                    content.supporting?.let { BlockSupporting(it, contentColor, sizes.supporting) }
                }
            }
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

@Composable
private fun BlockSupporting(text: String, color: ColorProvider, size: TextUnit) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Medium),
    )
}

/** Shown in the header-less compact layouts when both feed and sleep are disabled, so the widget is never blank. */
@Composable
private fun BabyNameFallback(babyName: String, size: TextUnit) {
    Text(
        text = babyName,
        maxLines = 1,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = size,
        ),
        modifier = GlanceModifier.fillMaxWidth().clickable(openHomeAction()),
    )
}

private fun feedBlockContent(
    data: WidgetData,
    now: Instant,
) = DomainBlockContent(
    emoji = FEED_EMOJI,
    label = feedLabel(data.lastFeedSide, data.feedState),
    value = feedValue(data.lastFeedStart, data.feedState, now),
    supporting = feedSupporting(data.lastFeedSide, data.feedState, data.lastFeedStart, now),
    description = feedContentDescription(data.lastFeedSide, data.feedState, data.lastFeedStart, now),
)

private fun sleepBlockContent(
    data: WidgetData,
    now: Instant,
) = DomainBlockContent(
    emoji = SLEEP_EMOJI,
    label = sleepLabel(data.sleepState),
    value = sleepValue(data.sleepState, data.sleepSince, now),
    supporting = sleepSupporting(data.sleepState, data.sleepSince, now),
    description = sleepContentDescription(data.sleepState, data.sleepSince, now),
)

private fun WidgetData.hasActiveFeed(): Boolean = feedState == FeedState.ACTIVE || feedState == FeedState.PAUSED

/** A tracker row is shown only when its feature is enabled. Disabled rows are omitted entirely. */
internal fun shouldShowFeedRow(data: WidgetData): Boolean = data.feedEnabled
internal fun shouldShowSleepRow(data: WidgetData): Boolean = data.sleepEnabled

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

internal fun feedSupporting(side: BreastSide?, state: FeedState, start: Instant?, now: Instant): String? =
    when (state) {
        FeedState.ACTIVE -> start?.let { "Started ${Duration.between(it, now).formatElapsedShort()} ago" }
        FeedState.PAUSED -> start?.let { "Paused, started ${Duration.between(it, now).formatElapsedShort()} ago" }
        FeedState.RECENT -> side?.let { "Last side ${it.label()}" }
        FeedState.NONE -> null
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

internal fun sleepSupporting(state: SleepState, since: Instant?, now: Instant): String? =
    when (state) {
        SleepState.NONE -> null
        SleepState.SLEEPING -> since?.let { "Asleep ${Duration.between(it, now).formatElapsedShort()}" } ?: "Asleep now"
        SleepState.AWAKE -> since?.let { "Awake ${Duration.between(it, now).formatElapsedShort()}" } ?: "Awake now"
    }

internal fun widgetStatusSummary(data: WidgetData, now: Instant): String =
    when {
        data.feedState == FeedState.ACTIVE -> feedValue(data.lastFeedStart, data.feedState, now)?.let { "Feeding $it" }
            ?: "Feeding"
        data.feedState == FeedState.PAUSED -> "Feed paused"
        data.sleepState == SleepState.SLEEPING -> sleepValue(data.sleepState, data.sleepSince, now)?.let { "Sleeping $it" }
            ?: "Sleeping"
        else -> "Ready"
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
