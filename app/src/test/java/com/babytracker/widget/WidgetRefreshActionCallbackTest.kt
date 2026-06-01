package com.babytracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetRefreshActionCallbackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val glanceId = TestGlanceId("widget-1")

    @Before
    fun setup() {
        BabyWidget.refreshingInstances.clear()
    }

    @Test
    fun `refresh marks widget refreshing before first render and schedules immediate work`() = runTest {
        var updateCalled = false
        var scheduled = false
        val handler = WidgetRefreshActionHandler(
            updateWidget = { _, id ->
                updateCalled = true
                assertTrue(BabyWidget.refreshingInstances.contains(id))
            },
            schedulerProvider = {
                testScheduler { scheduled = true }
            },
        )

        handler.refresh(context, glanceId)

        assertTrue(updateCalled)
        assertTrue(scheduled)
        assertTrue(BabyWidget.refreshingInstances.contains(glanceId))
    }

    @Test
    fun `refresh removes widget from refreshing set and skips scheduling when first render fails`() = runTest {
        var scheduled = false
        val handler = WidgetRefreshActionHandler(
            updateWidget = { _, _ -> error("render failed") },
            schedulerProvider = {
                testScheduler { scheduled = true }
            },
        )

        handler.refresh(context, glanceId)

        assertFalse(scheduled)
        assertFalse(BabyWidget.refreshingInstances.contains(glanceId))
    }

    private data class TestGlanceId(val value: String) : GlanceId

    private fun testScheduler(onSchedule: () -> Unit): WidgetRefreshScheduler =
        object : WidgetRefreshScheduler {
            override fun scheduleImmediateRefresh() {
                onSchedule()
            }
        }
}
