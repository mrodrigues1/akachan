package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerDashboardViewModelTest {

    private lateinit var fetchPartnerDataUseCase: FetchPartnerDataUseCase
    private lateinit var widgetUpdater: WidgetUpdater
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appContext: Context

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fetchPartnerDataUseCase = mockk()
        widgetUpdater = mockk { coJustRun { updateAll() } }
        settingsRepository = mockk { every { getVolumeUnit() } returns flowOf(VolumeUnit.ML) }
        appContext = mockk {
            every { getString(R.string.error_partner_refresh_no_data) } returns
                "We couldn't check for shared updates. Pull down to try again."
            every { getString(R.string.error_partner_refresh_stale) } returns
                "We couldn't check just now. Showing the last shared update."
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeSnapshot() = ShareSnapshot(
        lastSyncAt = Instant.now(),
        baby = BabySnapshot("Baby", Instant.now().toEpochMilli(), emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    @Test
    fun `init loads snapshot on creation`() = runTest {
        val snapshot = makeSnapshot()
        coEvery { fetchPartnerDataUseCase() } returns snapshot

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `refresh sets isDisconnected on IllegalStateException`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws IllegalStateException("Partner access revoked")

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)

        assertTrue(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `revoke triggers widget update so stale partner data is not shown`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws IllegalStateException("Partner access revoked")

        PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)

        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    @Test
    fun `successful refresh clears disconnected state`() = runTest {
        val snapshot = makeSnapshot()
        coEvery { fetchPartnerDataUseCase() }
            .throws(IllegalStateException("Partner access revoked"))
            .andThen(snapshot)

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)
        viewModel.refresh()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh sets error on generic exception`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws RuntimeException("Network error")

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)

        assertEquals(
            "We couldn't check for shared updates. Pull down to try again.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `partner data fetch failure is retryable and not disconnected`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws PartnerDataFetchException("Could not load partner data")

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)

        assertEquals(
            "We couldn't check for shared updates. Pull down to try again.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    @Test
    fun `refresh keeps snapshot and explains stale data when update fails`() = runTest {
        var callCount = 0
        val snapshot = makeSnapshot()
        coEvery { fetchPartnerDataUseCase() } answers {
            if (callCount++ == 0) {
                snapshot
            } else {
                throw IOException("Network error")
            }
        }

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)
        viewModel.refresh()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertEquals(
            "We couldn't check just now. Showing the last shared update.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `explicit refresh updates snapshot with latest data`() = runTest {
        val snapshot1 = makeSnapshot()
        val snapshot2 = makeSnapshot()
        coEvery { fetchPartnerDataUseCase() } returnsMany listOf(snapshot1, snapshot2)

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)
        viewModel.refresh()

        assertEquals(snapshot2, viewModel.uiState.value.snapshot)
    }

    @Test
    fun `refresh ignores duplicate request while loading`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { fetchPartnerDataUseCase() } coAnswers {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            makeSnapshot()
        }

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)
        fetchStarted.await()

        viewModel.refresh()

        coVerify(exactly = 1) { fetchPartnerDataUseCase() }
        releaseFetch.complete(Unit)
    }

    @Test
    fun `clearError removes error from state`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws RuntimeException("Network error")
        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase, widgetUpdater, settingsRepository, appContext)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
