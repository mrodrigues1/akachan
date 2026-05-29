package com.babytracker.widget

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WidgetRefreshWorkerTest {

    private val snapshot = ShareSnapshot(
        lastSyncAt = Instant.now(),
        baby = BabySnapshot("Akira", 0L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    private fun buildWorker(
        updater: WidgetUpdater,
        settings: SettingsRepository,
        fetchPartnerData: FetchPartnerDataUseCase,
        partnerCache: PartnerWidgetCache,
    ): WidgetRefreshWorker {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = WidgetRefreshWorker(
                        appContext,
                        workerParameters,
                        updater,
                        settings,
                        fetchPartnerData,
                        partnerCache,
                    )
                },
            )
            .build()
    }

    @Test
    fun `non-partner mode updates widgets without fetching and returns success`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk { coEvery { getAppMode() } returns flowOf(AppMode.NONE) }
        val fetch: FetchPartnerDataUseCase = mockk()
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { updater.updateAll() }
        coVerify(exactly = 0) { fetch(any<ShareCode>()) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `non-partner mode returns retry when updater throws`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } throws IllegalStateException("transient") }
        val settings: SettingsRepository = mockk { coEvery { getAppMode() } returns flowOf(AppMode.NONE) }
        val fetch: FetchPartnerDataUseCase = mockk()
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `partner success fetches and saves under the same share code`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } returns snapshot
        val cache: PartnerWidgetCache = mockk { coEvery { save(any(), any()) } just Runs }

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { fetch(ShareCode("CODE")) }
        coVerify(exactly = 1) { cache.save("CODE", any()) }
        coVerify(exactly = 1) { updater.updateAll() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `partner mode with null share code updates widgets and returns success without fetching`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf(null)
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { updater.updateAll() }
        coVerify(exactly = 0) { fetch(any<ShareCode>()) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `partner revoke clears the cache for the tried code and returns success without retry`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } throws PartnerAccessRevokedException("Partner access revoked")
        val cache: PartnerWidgetCache = mockk {
            coEvery { clear(any()) } just Runs
            coEvery { save(any(), any()) } just Runs
        }

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 1) { cache.clear("CODE") }
        coVerify(exactly = 0) { cache.save(any(), any()) }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `partner generic IllegalStateException retries and leaves cache untouched`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } throws IllegalStateException("boom")
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 0) { cache.save(any(), any()) }
        coVerify(exactly = 0) { cache.clear(any()) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `partner transient failure retries and leaves cache untouched`() = runTest {
        val updater: WidgetUpdater = mockk { coEvery { updateAll() } returns Unit }
        val settings: SettingsRepository = mockk {
            coEvery { getAppMode() } returns flowOf(AppMode.PARTNER)
            coEvery { getShareCode() } returns flowOf("CODE")
        }
        val fetch: FetchPartnerDataUseCase = mockk()
        coEvery { fetch(ShareCode("CODE")) } throws RuntimeException("network down")
        val cache: PartnerWidgetCache = mockk()

        val result = buildWorker(updater, settings, fetch, cache).doWork()

        coVerify(exactly = 0) { cache.save(any(), any()) }
        coVerify(exactly = 0) { cache.clear(any()) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
