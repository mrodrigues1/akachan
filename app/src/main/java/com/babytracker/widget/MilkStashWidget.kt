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

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, WIDE_SHORT_SIZE, WIDE_SIZE, TALL_SIZE, MEDIUM_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMilkStashWidgetData(context)
        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                val size = LocalSize.current
                when {
                    size.width >= MEDIUM_SIZE.width && size.height >= MEDIUM_SIZE.height ->
                        MilkStashMediumContent(data)
                    size.width >= WIDE_SHORT_SIZE.width ->
                        MilkStashWideContent(data)
                    size.height >= TALL_SIZE.height ->
                        MilkStashTallContent(data)
                    else ->
                        MilkStashSmallContent(data)
                }
            }
        }
    }

    companion object {
        val SMALL_SIZE: DpSize = DpSize(110.dp, 110.dp)
        val WIDE_SHORT_SIZE: DpSize = DpSize(180.dp, 64.dp)
        val WIDE_SIZE: DpSize = DpSize(180.dp, 110.dp)
        val TALL_SIZE: DpSize = DpSize(110.dp, 250.dp)
        val MEDIUM_SIZE: DpSize = DpSize(180.dp, 180.dp)
    }
}
