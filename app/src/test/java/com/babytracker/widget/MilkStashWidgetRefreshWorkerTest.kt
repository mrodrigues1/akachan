package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MilkStashWidgetRefreshWorkerTest {

    private fun buildWorker(updater: MilkStashWidgetUpdater): MilkStashWidgetRefreshWorker {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestListenableWorkerBuilder<MilkStashWidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = MilkStashWidgetRefreshWorker(appContext, workerParameters, updater)
                },
            )
            .build()
    }

    @Test
    fun `successful update returns success`() = runTest {
        val updater: MilkStashWidgetUpdater = mockk { coEvery { updateAll() } returns Unit }

        val result = buildWorker(updater).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `update failure returns retry`() = runTest {
        val updater: MilkStashWidgetUpdater = mockk {
            coEvery { updateAll() } throws IllegalStateException("glance host gone")
        }

        val result = buildWorker(updater).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
