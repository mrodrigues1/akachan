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
import java.util.concurrent.atomic.AtomicBoolean

class BabyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isRefreshing = refreshingActive.get()
        val data = loadWidgetData(context)
        val now = Instant.now()

        provideContent {
            GlanceTheme(colors = BabyWidgetColors) {
                WidgetBody(data = data, now = now, isRefreshing = isRefreshing)
            }
        }
    }

    @Composable
    private fun WidgetBody(data: WidgetData, now: Instant, isRefreshing: Boolean) {
        when (widgetLayoutForSize(LocalSize.current)) {
            WidgetLayout.COMPACT_WIDE -> SmallContent(data = data, now = now)
            WidgetLayout.COMPACT_NARROW -> SmallNarrowContent(data = data, now = now)
            WidgetLayout.MEDIUM -> MediumContent(data = data, now = now, isRefreshing = isRefreshing)
            WidgetLayout.TWO_BY_FOUR -> TwoByFourContent(data = data, now = now, isRefreshing = isRefreshing)
            WidgetLayout.THREE_BY_THREE -> ThreeByThreeContent(data = data, now = now, isRefreshing = isRefreshing)
            WidgetLayout.THREE_BY_FOUR -> ThreeByFourContent(data = data, now = now, isRefreshing = isRefreshing)
            WidgetLayout.FOUR_BY_TWO -> FourByTwoContent(data = data, now = now, isRefreshing = isRefreshing)
            WidgetLayout.FOUR_BY_THREE -> FourByThreeContent(data = data, now = now, isRefreshing = isRefreshing)
            WidgetLayout.FOUR_BY_FOUR -> FourByFourContent(data = data, now = now, isRefreshing = isRefreshing)
        }
    }

    companion object {
        val refreshingActive: AtomicBoolean = AtomicBoolean(false)

        val COMPACT_NARROW_SIZE: DpSize = DpSize(110.dp, 64.dp)
        val COMPACT_WIDE_SIZE: DpSize = DpSize(180.dp, 64.dp)
        val FOUR_BY_ONE_SIZE: DpSize = DpSize(250.dp, 64.dp)
        val MEDIUM_SIZE: DpSize = DpSize(110.dp, 110.dp)
        val THREE_BY_THREE_SIZE: DpSize = DpSize(180.dp, 180.dp)
        val TWO_BY_FOUR_SIZE: DpSize = DpSize(110.dp, 250.dp)
        val THREE_BY_FOUR_SIZE: DpSize = DpSize(180.dp, 250.dp)
        val FOUR_BY_TWO_SIZE: DpSize = DpSize(250.dp, 110.dp)
        val FOUR_BY_THREE_SIZE: DpSize = DpSize(250.dp, 180.dp)
        val FOUR_BY_FOUR_SIZE: DpSize = DpSize(250.dp, 250.dp)
        val RESPONSIVE_SIZES: Set<DpSize> = setOf(
            COMPACT_NARROW_SIZE,
            COMPACT_WIDE_SIZE,
            FOUR_BY_ONE_SIZE,
            MEDIUM_SIZE,
            THREE_BY_THREE_SIZE,
            TWO_BY_FOUR_SIZE,
            THREE_BY_FOUR_SIZE,
            FOUR_BY_TWO_SIZE,
            FOUR_BY_THREE_SIZE,
            FOUR_BY_FOUR_SIZE,
        )
    }
}

internal enum class WidgetLayout {
    COMPACT_WIDE,
    COMPACT_NARROW,
    MEDIUM,
    TWO_BY_FOUR,
    THREE_BY_THREE,
    THREE_BY_FOUR,
    FOUR_BY_TWO,
    FOUR_BY_THREE,
    FOUR_BY_FOUR,
}

internal val WidgetLayout.supportsRefreshButton: Boolean
    get() = this != WidgetLayout.COMPACT_NARROW && this != WidgetLayout.COMPACT_WIDE

internal fun widgetLayoutForSize(size: DpSize): WidgetLayout =
    when {
        size.width >= BabyWidget.FOUR_BY_FOUR_SIZE.width &&
            size.height >= BabyWidget.FOUR_BY_FOUR_SIZE.height -> WidgetLayout.FOUR_BY_FOUR
        size.width >= BabyWidget.FOUR_BY_THREE_SIZE.width &&
            size.height >= BabyWidget.FOUR_BY_THREE_SIZE.height -> WidgetLayout.FOUR_BY_THREE
        size.width >= BabyWidget.FOUR_BY_TWO_SIZE.width &&
            size.height >= BabyWidget.FOUR_BY_TWO_SIZE.height -> WidgetLayout.FOUR_BY_TWO
        size.width >= BabyWidget.THREE_BY_FOUR_SIZE.width &&
            size.height >= BabyWidget.THREE_BY_FOUR_SIZE.height -> WidgetLayout.THREE_BY_FOUR
        size.width >= BabyWidget.THREE_BY_THREE_SIZE.width &&
            size.height >= BabyWidget.THREE_BY_THREE_SIZE.height -> WidgetLayout.THREE_BY_THREE
        size.width < BabyWidget.THREE_BY_FOUR_SIZE.width &&
            size.height >= BabyWidget.TWO_BY_FOUR_SIZE.height -> WidgetLayout.TWO_BY_FOUR
        size.height >= BabyWidget.MEDIUM_SIZE.height -> WidgetLayout.MEDIUM
        size.width >= BabyWidget.COMPACT_WIDE_SIZE.width -> WidgetLayout.COMPACT_WIDE
        else -> WidgetLayout.COMPACT_NARROW
    }
