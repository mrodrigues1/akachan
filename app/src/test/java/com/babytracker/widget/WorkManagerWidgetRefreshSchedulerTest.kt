package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerWidgetRefreshSchedulerTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `scheduleImmediateRefresh enqueues a single unique one-time work`() {
        WorkManagerWidgetRefreshScheduler(context).scheduleImmediateRefresh()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerWidgetRefreshScheduler.IMMEDIATE_UNIQUE_NAME)
            .get()
        assertEquals(1, infos.size)
    }

    @Test
    fun `scheduleImmediateRefresh dedupes while one is already enqueued`() {
        val scheduler = WorkManagerWidgetRefreshScheduler(context)

        scheduler.scheduleImmediateRefresh()
        scheduler.scheduleImmediateRefresh()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerWidgetRefreshScheduler.IMMEDIATE_UNIQUE_NAME)
            .get()
        assertEquals(1, infos.size)
    }
}
