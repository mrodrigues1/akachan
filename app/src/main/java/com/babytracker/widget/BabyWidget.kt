package com.babytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.data.toWidgetData
import com.babytracker.widget.theme.BabyWidgetColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class BabyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val babyName = entryPoint.babyRepository().getBabyProfile().first()?.name
        val lastFeed = entryPoint.breastfeedingRepository().getLastSession()
        val latestSleep = entryPoint.sleepRepository().getLatestRecord()
        val widgetData = toWidgetData(babyName, lastFeed, latestSleep)

        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                WidgetContent(widgetData)
            }
        }
    }

    companion object {
        val SMALL_SIZE: DpSize = DpSize(110.dp, 40.dp)
        val MEDIUM_SIZE: DpSize = DpSize(180.dp, 110.dp)
    }
}

@Composable
private fun WidgetContent(data: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.babyName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        )
        val feedText = if (data.lastFeedStart != null) {
            "Fed · ${data.lastFeedSide?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "–"}"
        } else {
            "No feeds yet"
        }
        Text(
            text = feedText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
            ),
        )
        val sleepText = when (data.sleepState) {
            SleepState.SLEEPING -> "Sleeping"
            SleepState.AWAKE -> "Awake"
            SleepState.NONE -> null
        }
        if (sleepText != null) {
            Text(
                text = sleepText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}
