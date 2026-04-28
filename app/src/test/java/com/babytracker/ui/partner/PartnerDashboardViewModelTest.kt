package com.babytracker.ui.partner

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PartnerDashboardViewModelTest {

    private lateinit var fetchPartnerDataUseCase: FetchPartnerDataUseCase

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fetchPartnerDataUseCase = mockk()
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

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase)

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `refresh sets isDisconnected on IllegalStateException`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws IllegalStateException("Partner access revoked")

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase)

        assertTrue(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh sets error on generic exception`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws RuntimeException("Network error")

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase)

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDisconnected)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `explicit refresh updates snapshot with latest data`() = runTest {
        val snapshot1 = makeSnapshot()
        val snapshot2 = makeSnapshot()
        coEvery { fetchPartnerDataUseCase() } returnsMany listOf(snapshot1, snapshot2)

        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase)
        viewModel.refresh()

        assertEquals(snapshot2, viewModel.uiState.value.snapshot)
    }

    @Test
    fun `clearError removes error from state`() = runTest {
        coEvery { fetchPartnerDataUseCase() } throws RuntimeException("Network error")
        val viewModel = PartnerDashboardViewModel(fetchPartnerDataUseCase)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
