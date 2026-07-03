package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MergedFeedHistory
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.DeletePartnerFeedUseCase
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.sharing.usecase.PartnerDataFetchException
import com.babytracker.testutil.MainDispatcherExtension
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerFeedHistoryViewModelTest {

    private val observePartnerData = mockk<ObservePartnerDataUseCase>()
    private val observePartnerFeedHistory = mockk<ObservePartnerFeedHistoryUseCase>()
    private val deletePartnerFeed = mockk<DeletePartnerFeedUseCase>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val widgetUpdater = mockk<WidgetUpdater>()
    private val appContext = mockk<Context>()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    @BeforeEach
    fun setup() {
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        every { observePartnerData() } returns flowOf(snapshot())
        coJustRun { deletePartnerFeed(any()) }
        coJustRun { widgetUpdater.updateAll() }
        every { appContext.getString(R.string.error_partner_load_failed) } returns "load-failed"
        every { appContext.getString(R.string.error_partner_delete_failed) } returns "delete-failed"
        every { appContext.getString(R.string.error_partner_access_revoked) } returns "access-revoked"
    }

    @Test
    fun `merged entries land in state`() = runTest {
        val entry = feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(entries = listOf(entry)))

        val viewModel = viewModel()

        assertEquals(listOf(entry), viewModel.uiState.value.entries)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `milk bags are sourced from the live snapshot`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        every { observePartnerData() } returns flowOf(snapshot().copy(milkBags = listOf(MilkBagSnapshot(1L, 0L, 120, null))))

        val viewModel = viewModel()

        assertEquals(1, viewModel.uiState.value.milkBags.size)
    }

    @Test
    fun `isEditable is true only for partner entries with clientId`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        val viewModel = viewModel()

        assertTrue(viewModel.isEditable(feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)))
        assertFalse(viewModel.isEditable(feed(clientId = "entry-1", author = FeedAuthor.OWNER.name)))
        assertFalse(viewModel.isEditable(feed(clientId = "", author = FeedAuthor.PARTNER.name)))
    }

    @Test
    fun `listener access revoked exception sets accessRevoked`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flow { throw PartnerAccessRevokedException("revoked") }

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.accessRevoked)
        coVerify { widgetUpdater.updateAll() }
    }

    @Test
    fun `listener feed history exception clears loading and shows error`() = runTest {
        coEvery { observePartnerFeedHistory(any()) } returns flow { throw PartnerDataFetchException("Could not load feed history") }

        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.accessRevoked)
        assertEquals("load-failed", viewModel.uiState.value.error)
    }

    @Test
    fun `delete delegates to DeletePartnerFeedUseCase`() = runTest {
        val entry = feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        val viewModel = viewModel()

        viewModel.onDelete(entry)

        coVerify { deletePartnerFeed(entry) }
    }

    @Test
    fun `delete revoked failure sets accessRevoked and updates widgets`() = runTest {
        val entry = feed(clientId = "entry-1", author = FeedAuthor.PARTNER.name)
        coEvery { observePartnerFeedHistory(any()) } returns flowOf(MergedFeedHistory(emptyList()))
        coEvery { deletePartnerFeed(entry) } throws PartnerAccessRevokedException("Partner access revoked")
        val viewModel = viewModel()

        viewModel.onDelete(entry)

        assertTrue(viewModel.uiState.value.accessRevoked)
        coVerify { widgetUpdater.updateAll() }
    }

    private fun viewModel() = PartnerFeedHistoryViewModel(
        observePartnerData = observePartnerData,
        observePartnerFeedHistory = observePartnerFeedHistory,
        deletePartnerFeed = deletePartnerFeed,
        widgetUpdater = widgetUpdater,
        settingsRepository = settingsRepository,
        appContext = appContext,
    )

    private fun snapshot() = ShareSnapshot(
        lastSyncAt = Instant.ofEpochMilli(1_000),
        baby = BabySnapshot(name = "Baby", birthDateMs = 0L, allergies = emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    private fun feed(clientId: String, author: String) = BottleFeedSnapshot(
        timestamp = 2_000L, volumeMl = 90, type = FeedType.FORMULA.name, clientId = clientId, author = author, notes = null,
    )
}
