package com.babytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
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
            WidgetTestContent { MediumContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertHasClickAction()
    }

    @Test
    fun `medium content shows progress instead of refresh action while refreshing`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { MediumContent(data, now, isRefreshing = true) }
        }
        onNode(hasText(UPDATING_LABEL)).assertExists()
        onNode(hasContentDescription(UPDATING_DESCRIPTION)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertDoesNotExist()
    }

    @Test
    fun `two by four content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { TwoByFourContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `three by three content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { ThreeByThreeContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `three by four content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { ThreeByFourContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `four by two content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { FourByTwoContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `four by three content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { FourByThreeContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `four by four content header shows baby name and refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { FourByFourContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertExists()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertExists()
    }

    @Test
    fun `small content does not show baby name or refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { SmallContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertDoesNotExist()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertDoesNotExist()
    }

    @Test
    fun `small narrow content does not show baby name or refresh`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            WidgetTestContent { SmallNarrowContent(data, now) }
        }
        onNode(hasText(BABY_NAME)).assertDoesNotExist()
        onNode(hasContentDescription(REFRESH_DESCRIPTION)).assertDoesNotExist()
    }

    @Composable
    private fun WidgetTestContent(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalContext provides ApplicationProvider.getApplicationContext<Context>()) {
            GlanceTheme(colors = BabyWidgetColors) { content() }
        }
    }

    private companion object {
        const val BABY_NAME = "Akira"
        const val REFRESH_DESCRIPTION = "Refresh widget"
        const val UPDATING_DESCRIPTION = "Updating widget"
        const val UPDATING_LABEL = "Updating"
    }
}
