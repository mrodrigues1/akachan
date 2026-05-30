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

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

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
        when (widgetLayoutForSize(LocalSize.current)) {
            WidgetLayout.COMPACT_WIDE -> SmallContent(data = data, now = now)
            WidgetLayout.COMPACT_NARROW -> SmallNarrowContent(data = data, now = now)
            WidgetLayout.MEDIUM -> MediumContent(data = data, now = now)
        }
    }

    companion object {
        val COMPACT_NARROW_SIZE: DpSize = DpSize(110.dp, 64.dp)
        val COMPACT_WIDE_SIZE: DpSize = DpSize(180.dp, 64.dp)
        val MEDIUM_SIZE: DpSize = DpSize(110.dp, 110.dp)
        val RESPONSIVE_SIZES: Set<DpSize> = setOf(COMPACT_NARROW_SIZE, COMPACT_WIDE_SIZE, MEDIUM_SIZE)
    }
}

internal enum class WidgetLayout {
    COMPACT_WIDE,
    COMPACT_NARROW,
    MEDIUM,
}

internal fun widgetLayoutForSize(size: DpSize): WidgetLayout =
    when {
        size.height >= BabyWidget.MEDIUM_SIZE.height -> WidgetLayout.MEDIUM
        size.width >= BabyWidget.COMPACT_WIDE_SIZE.width -> WidgetLayout.COMPACT_WIDE
        else -> WidgetLayout.COMPACT_NARROW
    }
