package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetRefreshWorkerTest {

    @Test
    fun `doWork delegates to WidgetUpdater and returns success`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = WidgetRefreshWorker(appContext, workerParameters, updater)
                },
            )
            .build()

        val result = worker.doWork()

        coVerify(exactly = 1) { updater.updateAll() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns retry when updater throws`() = runTest {
        val updater: WidgetUpdater = mockk {
            coEvery { updateAll() } throws IllegalStateException("transient")
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = WidgetRefreshWorker(appContext, workerParameters, updater)
                },
            )
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
