package com.babytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.theme.BabyWidgetColors
import java.time.Instant

class BabyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val now = Instant.now()

        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                WidgetBody(data = data, now = now)
            }
        }
    }

    @Composable
    private fun WidgetBody(data: WidgetData, now: Instant) {
        val size = LocalSize.current
        if (size.width >= MEDIUM_SIZE.width && size.height >= MEDIUM_SIZE.height) {
            MediumContent(data = data, now = now)
        } else {
            SmallContent(data = data, now = now)
        }
    }

    companion object {
        val SMALL_SIZE: DpSize = DpSize(110.dp, 40.dp)
        val MEDIUM_SIZE: DpSize = DpSize(180.dp, 110.dp)
    }
}
