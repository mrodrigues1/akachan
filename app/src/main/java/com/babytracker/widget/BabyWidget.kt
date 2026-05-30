package com.babytracker.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.babytracker.widget.theme.BabyWidgetColors
import java.time.Instant

class BabyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(MEDIUM_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val now = Instant.now()

        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                MediumContent(data = data, now = now)
            }
        }
    }

    companion object {
        val MEDIUM_SIZE: DpSize = DpSize(110.dp, 110.dp)
    }
}
