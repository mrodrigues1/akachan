package com.babytracker.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.babytracker.widget.theme.BabyWidgetColors

class MilkStashWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL_SIZE, MEDIUM_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMilkStashWidgetData(context)
        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                val size = LocalSize.current
                if (size.width >= MEDIUM_SIZE.width && size.height >= MEDIUM_SIZE.height) {
                    MilkStashMediumContent(data)
                } else {
                    MilkStashSmallContent(data)
                }
            }
        }
    }

    companion object {
        val SMALL_SIZE: DpSize = DpSize(110.dp, 110.dp)
        val MEDIUM_SIZE: DpSize = DpSize(180.dp, 180.dp)
    }
}
