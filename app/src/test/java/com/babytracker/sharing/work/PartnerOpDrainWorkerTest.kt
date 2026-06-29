package com.babytracker.sharing.work

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.babytracker.sharing.usecase.ProcessFeedOpsUseCase
import com.babytracker.sharing.usecase.ProcessSleepOpsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PartnerOpDrainWorkerTest {

    private fun buildWorker(
        processSleepOps: ProcessSleepOpsUseCase,
        processFeedOps: ProcessFeedOpsUseCase,
    ): PartnerOpDrainWorker {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestListenableWorkerBuilder<PartnerOpDrainWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = PartnerOpDrainWorker(
                        appContext,
                        workerParameters,
                        processSleepOps,
                        processFeedOps,
                    )
                },
            )
            .build()
    }

    @Test
    fun `drains both inboxes and returns success`() = runTest {
        val sleep: ProcessSleepOpsUseCase = mockk { coEvery { drainOnce() } returns Unit }
        val feed: ProcessFeedOpsUseCase = mockk { coEvery { drainOnce() } returns Unit }

        val result = buildWorker(sleep, feed).doWork()

        coVerify(exactly = 1) { sleep.drainOnce() }
        coVerify(exactly = 1) { feed.drainOnce() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `returns retry when a drain throws`() = runTest {
        val sleep: ProcessSleepOpsUseCase = mockk { coEvery { drainOnce() } throws RuntimeException("offline") }
        val feed: ProcessFeedOpsUseCase = mockk()

        val result = buildWorker(sleep, feed).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
