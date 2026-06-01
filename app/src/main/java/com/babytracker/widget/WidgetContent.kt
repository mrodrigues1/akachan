package com.babytracker.widget

import androidx.compose.runtime.Composable
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
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
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NarrowDomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            label = "Feed",
            contentColor = if (feedingActive) {
                GlanceTheme.colors.onPrimary
            } else {
                GlanceTheme.colors.onPrimaryContainer
            },
            onClick = openBreastfeedingAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
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

/**
 * Compact wide widget: two side-by-side domain tiles for one-row launchers. The elapsed
 * value stays as the glance hero while both feed and sleep remain visible.
 */
@Composable
fun SmallContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) {
                GlanceTheme.colors.onPrimary
            } else {
                GlanceTheme.colors.onPrimaryContainer
            },
            sizes = SmallBlockSizes,
            onClick = openBreastfeedingAction(),
            stacked = true,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = SmallBlockSizes,
            onClick = openSleepAction(),
            stacked = true,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
}

/**
 * Medium widget: a quiet baby-name header over two full-width domain tiles. Each tile leads
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
            RefreshButton()
        }
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
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = MediumBlockSizes,
            onClick = openSleepAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

/**
 * Two-by-four widget: two full-width domain tiles with no header or footer. The extra vertical
 * space is divided equally between the feeding and sleep tiles so each timer reads large and
 * uncluttered. Header → removed to reduce cognitive load at a glance.
 */
@Composable
fun TwoByFourContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(12.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            RefreshButton()
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
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
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = MediumBlockSizes,
            onClick = openSleepAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

@Composable
fun ThreeByThreeContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
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
            RefreshButton()
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) {
                GlanceTheme.colors.onPrimary
            } else {
                GlanceTheme.colors.onPrimaryContainer
            },
            sizes = ThreeByThreeBlockSizes,
            onClick = openBreastfeedingAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = ThreeByThreeBlockSizes,
            onClick = openSleepAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

@Composable
fun ThreeByFourContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
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
            RefreshButton()
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) {
                GlanceTheme.colors.onPrimary
            } else {
                GlanceTheme.colors.onPrimaryContainer
            },
            sizes = ThreeByThreeBlockSizes,
            onClick = openBreastfeedingAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = ThreeByThreeBlockSizes,
            onClick = openSleepAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

@Composable
fun FourByTwoContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WidgetHeader(data = data, status = widgetStatusSummary(data, now), titleSize = 13.sp, statusSize = 10.sp)
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainBlock(
                backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
                content = feedBlockContent(data, now),
                contentColor = if (feedingActive) {
                    GlanceTheme.colors.onPrimary
                } else {
                    GlanceTheme.colors.onPrimaryContainer
                },
                sizes = FourByTwoBlockSizes,
                onClick = openBreastfeedingAction(),
                stacked = true,
                showSupporting = true,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            DomainBlock(
                backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
                content = sleepBlockContent(data, now),
                contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
                sizes = FourByTwoBlockSizes,
                onClick = openSleepAction(),
                stacked = true,
                showSupporting = true,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
    }
}

@Composable
fun FourByThreeContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
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
            RefreshButton()
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainBlock(
                backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
                content = feedBlockContent(data, now),
                contentColor = if (feedingActive) {
                    GlanceTheme.colors.onPrimary
                } else {
                    GlanceTheme.colors.onPrimaryContainer
                },
                sizes = FourByThreeBlockSizes,
                onClick = openBreastfeedingAction(),
                stacked = true,
                showSupporting = false,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            DomainBlock(
                backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
                content = sleepBlockContent(data, now),
                contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
                sizes = FourByThreeBlockSizes,
                onClick = openSleepAction(),
                stacked = true,
                showSupporting = false,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
    }
}

@Composable
fun FourByFourContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val feedingActive = data.hasActiveFeed()
    val sleeping = data.sleepState == SleepState.SLEEPING
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
            RefreshButton()
        }
        Spacer(modifier = GlanceModifier.height(12.dp))
        DomainBlock(
            backgroundRes = if (feedingActive) R.drawable.widget_feed_active else R.drawable.widget_feed_badge,
            content = feedBlockContent(data, now),
            contentColor = if (feedingActive) {
                GlanceTheme.colors.onPrimary
            } else {
                GlanceTheme.colors.onPrimaryContainer
            },
            sizes = FourByFourBlockSizes,
            onClick = openBreastfeedingAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            content = sleepBlockContent(data, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = FourByFourBlockSizes,
            onClick = openSleepAction(),
            showSupporting = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

@Composable
private fun WidgetHeader(data: WidgetData, status: String, titleSize: TextUnit, statusSize: TextUnit) {
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
        RefreshButton()
    }
}

@Composable
private fun RefreshButton() {
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
