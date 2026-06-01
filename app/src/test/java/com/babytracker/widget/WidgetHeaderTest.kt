package com.babytracker.widget

import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import com.babytracker.widget.data.WidgetData
import com.babytracker.widget.theme.BabyWidgetColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WidgetHeaderTest {

    private val now: Instant = Instant.parse("2026-06-01T12:00:00Z")
    private val data: WidgetData = WidgetData.EMPTY.copy(babyName = BABY_NAME)

    @Test
    fun `medium content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { MediumContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `two by four content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { TwoByFourContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `three by three content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { ThreeByThreeContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `three by four content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { ThreeByFourContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `four by two content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { FourByTwoContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `four by three content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { FourByThreeContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `four by four content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { FourByFourContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `small content does not show baby name or refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { SmallContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertDoesNotExist()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertDoesNotExist()
    }

    @Test
    fun `small narrow content does not show baby name or refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            GlanceTheme(colors = BabyWidgetColors) { SmallNarrowContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertDoesNotExist()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertDoesNotExist()
    }

    private companion object {
        const val BABY_NAME = "Akira"
        const val REFRESH_DESCRIPTION = "Refresh widget"
    }
}
