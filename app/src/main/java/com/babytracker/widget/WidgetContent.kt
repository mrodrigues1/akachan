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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.util.formatElapsedShort
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import java.time.Duration
import java.time.Instant

private const val FEED_EMOJI = "🍼"
private const val SLEEP_EMOJI = "🌙"

/** Text scale presets for a [DomainBlock], so each size variant tunes the hero hierarchy in one place. */
private data class BlockTextSizes(val emoji: TextUnit, val label: TextUnit, val value: TextUnit)

private val SmallBlockSizes = BlockTextSizes(emoji = 14.sp, label = 10.sp, value = 16.sp)
private val MediumBlockSizes = BlockTextSizes(emoji = 18.sp, label = 11.sp, value = 20.sp)

/**
 * Bolder widget: the elapsed value is the glance hero (large + Bold, domain-tinted), the
 * label is demoted to a small supporting line. Sleep gets an active-state signal: while the
 * baby is asleep *now* the sleep surface fills solid Sleep Blue (mirrors the SideSelector
 * selected grammar), so "asleep right now" reads without reading any text.
 */
@Composable
fun SmallContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    val sleeping = data.sleepState == SleepState.SLEEPING
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DomainBlock(
            backgroundRes = R.drawable.widget_feed_badge,
            emoji = FEED_EMOJI,
            label = feedLabel(data.lastFeedSide),
            value = feedValue(data.lastFeedStart, now),
            contentColor = GlanceTheme.colors.onPrimaryContainer,
            sizes = SmallBlockSizes,
            onClick = openBreastfeedingAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            emoji = SLEEP_EMOJI,
            label = sleepLabel(data.sleepState),
            value = sleepValue(data.sleepState, data.sleepSince, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = SmallBlockSizes,
            onClick = openSleepAction(),
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        )
    }
}

/**
 * Medium (2×2): a quiet baby-name header over two full-width domain tiles. Each tile leads
 * with its emoji, a small label, and the elapsed value rendered large + Bold as the hero.
 * The sleep tile fills solid Sleep Blue while the baby is asleep now. Header → Home, feed
 * tile → Breastfeeding, sleep tile → Sleep.
 */
@Composable
fun MediumContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
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
        Spacer(modifier = GlanceModifier.height(6.dp))
        DomainBlock(
            backgroundRes = R.drawable.widget_feed_badge,
            emoji = FEED_EMOJI,
            label = feedLabel(data.lastFeedSide),
            value = feedValue(data.lastFeedStart, now),
            contentColor = GlanceTheme.colors.onPrimaryContainer,
            sizes = MediumBlockSizes,
            onClick = openBreastfeedingAction(),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        DomainBlock(
            backgroundRes = if (sleeping) R.drawable.widget_sleep_active else R.drawable.widget_sleep_badge,
            emoji = SLEEP_EMOJI,
            label = sleepLabel(data.sleepState),
            value = sleepValue(data.sleepState, data.sleepSince, now),
            contentColor = if (sleeping) GlanceTheme.colors.onSecondary else GlanceTheme.colors.onSecondaryContainer,
            sizes = MediumBlockSizes,
            onClick = openSleepAction(),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

/**
 * A tinted domain surface that fills its modifier-defined bounds as one tap target. The
 * emoji anchors the domain; the label sits small above the large, Bold elapsed value. All
 * text shares [contentColor] so hierarchy is carried by scale and weight, not by hue.
 */
@Composable
private fun DomainBlock(
    backgroundRes: Int,
    emoji: String,
    label: String,
    value: String?,
    contentColor: ColorProvider,
    sizes: BlockTextSizes,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier
            .background(ImageProvider(backgroundRes))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, style = TextStyle(fontSize = sizes.emoji))
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = contentColor, fontSize = sizes.label, fontWeight = FontWeight.Medium),
            )
            if (value != null) {
                Text(
                    text = value,
                    maxLines = 1,
                    style = TextStyle(color = contentColor, fontSize = sizes.value, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

internal fun BreastSide.label(): String = when (this) {
    BreastSide.LEFT -> "Left"
    BreastSide.RIGHT -> "Right"
}

// --- Pure label/value builders, shared by both sizes (unit-tested in WidgetContentHelpersTest) ---

internal fun feedLabel(side: BreastSide?): String = side?.label() ?: "No feeds yet"

internal fun feedValue(start: Instant?, now: Instant): String? =
    start?.let { Duration.between(it, now).formatElapsedShort() }

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
