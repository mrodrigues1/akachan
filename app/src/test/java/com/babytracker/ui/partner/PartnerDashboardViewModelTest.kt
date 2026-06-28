package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerDashboardViewModelTest {

    private lateinit var observePartnerData: ObservePartnerDataUseCase
    private lateinit var observePartnerFeedHistory: ObservePartnerFeedHistoryUseCase
    private lateinit var widgetUpdater: WidgetUpdater
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appContext: Context

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        observePartnerData = mockk()
        observePartnerFeedHistory = mockk()
        widgetUpdater = mockk { coJustRun { updateAll() } }
        settingsRepository = mockk { every { getVolumeUnit() } returns flowOf(VolumeUnit.ML) }
        appContext = mockk {
            every { getString(R.string.error_partner_refresh_no_data) } returns
                "We couldn't check for shared updates. Pull down to try again."
            every { getString(R.string.error_partner_refresh_stale) } returns
                "We couldn't check just now. Showing the last shared update."
        }
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
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

    private fun build() = PartnerDashboardViewModel(
        observePartnerData, observePartnerFeedHistory, widgetUpdater, settingsRepository, appContext,
    )

    @Test
    fun `init streams the live snapshot`() = runTest {
        val snapshot = makeSnapshot()
        every { observePartnerData() } returns flowOf(snapshot)

        val viewModel = build()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `revoke sets disconnected and updates widgets`() = runTest {
        every { observePartnerData() } returns flow { throw PartnerAccessRevokedException("revoked") }

        val viewModel = build()

        assertTrue(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    @Test
    fun `fetch failure shows retryable error without disconnecting`() = runTest {
        every { observePartnerData() } returns flow { throw PartnerDataFetchException("Could not load partner data") }

        val viewModel = build()

        assertEquals(
            "We couldn't check for shared updates. Pull down to try again.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
    }

    @Test
    fun `retry restarts the collection and clears the disconnected state`() = runTest {
        val snapshot = makeSnapshot()
        every { observePartnerData() } returnsMany listOf(
            flow { throw PartnerAccessRevokedException("revoked") },
            flowOf(snapshot),
        )

        val viewModel = build()
        assertTrue(viewModel.uiState.value.isDisconnected)

        viewModel.retry()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isDisconnected)
    }

    @Test
    fun `bottle feed overlay is applied to the snapshot`() = runTest {
        val snapshot = makeSnapshot()
        val overlaid = com.babytracker.sharing.domain.model.BottleFeedSnapshot(
            timestamp = 5_000L, volumeMl = 120, type = "FORMULA", clientId = "c1", author = "PARTNER",
        )
        every { observePartnerData() } returns flowOf(snapshot)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(listOf(overlaid)))

        val viewModel = build()

        assertEquals(listOf(overlaid), viewModel.uiState.value.snapshot?.bottleFeeds)
    }

    @Test
    fun `clearError removes the error`() = runTest {
        every { observePartnerData() } returns flow { throw PartnerDataFetchException("x") }
        val viewModel = build()

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
