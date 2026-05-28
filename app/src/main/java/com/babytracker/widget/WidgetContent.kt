package com.babytracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.babytracker.util.formatDuration
import com.babytracker.util.formatElapsedAgo
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import java.time.Duration
import java.time.Instant

private val ICON_SIZE = 20.dp
private val ICON_TEXT_SPACER = 8.dp
private val ROW_SPACER = 8.dp
private val COMPACT_SECTION_SPACER = 12.dp

// omits `now` — shows static labels only; elapsed text would freeze without a periodic refresh
@Composable
fun SmallContent(data: WidgetData, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactFeedContent(data = data)
        if (data.sleepState != SleepState.NONE) {
            Spacer(modifier = GlanceModifier.width(COMPACT_SECTION_SPACER))
            CompactSleepContent(data = data)
        }
    }
}

@Composable
fun MediumContent(data: WidgetData, now: Instant, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp),
    ) {
        Text(
            text = data.babyName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.height(ROW_SPACER))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            FeedRowContent(data = data, now = now)
        }
        Spacer(modifier = GlanceModifier.height(ROW_SPACER))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            SleepRowContent(data = data, now = now)
        }
    }
}

@Composable
private fun CompactFeedContent(data: WidgetData) {
    val side = data.lastFeedSide
    if (side == null) {
        EmptyText("No feeds yet")
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_notif_breastfeeding),
            contentDescription = null,
            modifier = GlanceModifier.size(ICON_SIZE),
        )
        Spacer(modifier = GlanceModifier.width(ICON_TEXT_SPACER))
        Text(
            text = side.label(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        )
    }
}

@Composable
private fun CompactSleepContent(data: WidgetData) {
    val label = if (data.sleepState == SleepState.SLEEPING) "Sleeping" else "Awake"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_notif_sleep),
            contentDescription = null,
            modifier = GlanceModifier.size(ICON_SIZE),
        )
        Spacer(modifier = GlanceModifier.width(ICON_TEXT_SPACER))
        Text(
            text = label,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        )
    }
}

@Composable
private fun FeedRowContent(data: WidgetData, now: Instant) {
    val side = data.lastFeedSide
    val start = data.lastFeedStart
    if (side == null || start == null) {
        EmptyText("No feeds yet")
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_notif_breastfeeding),
            contentDescription = null,
            modifier = GlanceModifier.size(ICON_SIZE),
        )
        Spacer(modifier = GlanceModifier.width(ICON_TEXT_SPACER))
        Text(
            text = "${side.label()} · ${Duration.between(start, now).formatElapsedAgo()}",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        )
    }
}

@Composable
private fun SleepRowContent(data: WidgetData, now: Instant) {
    when (data.sleepState) {
        SleepState.NONE -> EmptyText("No sleep logged")
        SleepState.SLEEPING -> {
            Image(
                provider = ImageProvider(R.drawable.ic_notif_sleep),
                contentDescription = null,
                modifier = GlanceModifier.size(ICON_SIZE),
            )
            Spacer(modifier = GlanceModifier.width(ICON_TEXT_SPACER))
            val since = data.sleepSince ?: return
            Text(
                text = "Sleeping · ${Duration.between(since, now).formatDuration()}",
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 14.sp),
            )
        }
        SleepState.AWAKE -> {
            Image(
                provider = ImageProvider(R.drawable.ic_notif_sleep),
                contentDescription = null,
                modifier = GlanceModifier.size(ICON_SIZE),
            )
            Spacer(modifier = GlanceModifier.width(ICON_TEXT_SPACER))
            val since = data.sleepSince ?: return
            Text(
                text = "Awake since ${Duration.between(since, now).formatElapsedAgo()}",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 13.sp,
        ),
    )
}

internal fun BreastSide.label(): String = when (this) {
    BreastSide.LEFT -> "Left"
    BreastSide.RIGHT -> "Right"
}
