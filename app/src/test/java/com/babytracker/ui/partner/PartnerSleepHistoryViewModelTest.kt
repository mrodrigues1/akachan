package com.babytracker.ui.partner

import android.content.Context
import app.cash.turbine.test
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.MergedSleepHistory
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCase
import com.babytracker.testutil.MainDispatcherExtension
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerSleepHistoryViewModelTest {
    private val observePartnerData: ObservePartnerDataUseCase = mockk()
    private val observeHistory: ObservePartnerSleepHistoryUseCase = mockk()
    private val widgetUpdater: WidgetUpdater = mockk(relaxed = true)
    private val appContext: Context = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    private fun row(clientId: String, author: SleepAuthor) = SleepSnapshot(
        id = 0, startTime = 1_000L, endTime = 2_000L, sleepType = SleepType.NAP, notes = null,
        clientId = clientId, startedBy = author,
    )

    @BeforeEach
    fun setUp() {
        every { appContext.getString(any()) } returns "error"
        every { observePartnerData() } returns flowOf(
            ShareSnapshot(
                lastSyncAt = Instant.EPOCH,
                baby = BabySnapshot("Mia", 0L, emptyList()),
                sessions = emptyList(),
                sleepRecords = emptyList(),
            ),
        )
    }

    @Test
    fun `entries populate from the merged history once uiState is collected`() = runTest {
        val rows = listOf(row("p", SleepAuthor.PARTNER), row("o", SleepAuthor.OWNER))
        coEvery { observeHistory(any()) } returns flowOf(MergedSleepHistory(rows, emptySet()))

        val vm = PartnerSleepHistoryViewModel(observePartnerData, observeHistory, widgetUpdater, appContext)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(rows, state.entries)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pipeline stays cold until uiState has a subscriber`() = runTest {
        coEvery { observeHistory(any()) } returns flowOf(MergedSleepHistory(emptyList(), emptySet()))

        PartnerSleepHistoryViewModel(observePartnerData, observeHistory, widgetUpdater, appContext)
        advanceUntilIdle()

        // WhileSubscribed: with nobody collecting uiState, the snapshot pipeline never attaches.
        verify(exactly = 0) { observePartnerData() }
    }

    @Test
    fun `isEditable is true only for a partner-started row with a clientId`() = runTest {
        coEvery { observeHistory(any()) } returns flowOf(MergedSleepHistory(emptyList(), emptySet()))
        val vm = PartnerSleepHistoryViewModel(observePartnerData, observeHistory, widgetUpdater, appContext)

        assertTrue(vm.isEditable(row("p", SleepAuthor.PARTNER)))
        assertFalse(vm.isEditable(row("o", SleepAuthor.OWNER)))
        assertFalse(vm.isEditable(row("", SleepAuthor.PARTNER)))
    }
}
