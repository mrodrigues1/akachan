package com.babytracker.ui.sharing

import android.content.Context
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.usecase.GenerateShareCodeUseCase
import com.babytracker.sharing.usecase.RevokePartnerUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

class ManageSharingViewModelTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var service: FirestoreSharingService
    private lateinit var generateShareCodeUseCase: GenerateShareCodeUseCase
    private lateinit var revokePartnerUseCase: RevokePartnerUseCase
    private lateinit var appContext: Context

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        service = mockk()
        generateShareCodeUseCase = mockk()
        revokePartnerUseCase = mockk()
        appContext = mockk(relaxed = true)
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)
        every { settingsRepository.getShareCode() } returns flowOf(null)
    }

    private fun createViewModel() = ManageSharingViewModel(
        settingsRepository = settingsRepository,
        service = service,
        generateShareCodeUseCase = generateShareCodeUseCase,
        revokePartnerUseCase = revokePartnerUseCase,
        appContext = appContext,
    )

    @Test
    fun `refresh does nothing when appMode is not PRIMARY`() = runTest {
        val viewModel = createViewModel()

        viewModel.refresh()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(emptyList<PartnerInfo>(), viewModel.uiState.value.partners)
    }

    @Test
    fun `refresh loads partners when PRIMARY`() = runTest {
        val code = "ABCD1234"
        val partners = listOf(PartnerInfo("uid1", Instant.now()))
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        every { settingsRepository.getShareCode() } returns flowOf(code)
        coEvery { service.getPartners(code) } returns partners

        val viewModel = createViewModel()
        viewModel.refresh()

        assertEquals(partners, viewModel.uiState.value.partners)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `startSharing calls use case and loads partners`() = runTest {
        val code = "ABCD1234"
        val partners = listOf(PartnerInfo("uid1", Instant.now()))
        coJustRun { generateShareCodeUseCase() }
        every { settingsRepository.getShareCode() } returns flowOf(code)
        coEvery { service.getPartners(code) } returns partners

        val viewModel = createViewModel()
        viewModel.startSharing()

        coVerify { generateShareCodeUseCase() }
        assertEquals(partners, viewModel.uiState.value.partners)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `startSharing sets error on failure`() = runTest {
        coEvery { generateShareCodeUseCase() } throws RuntimeException("Network error")

        val viewModel = createViewModel()
        viewModel.startSharing()

        assertNotNull(viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `startSharing does not map cancellation to an error`() = runTest {
        coEvery { generateShareCodeUseCase() } throws CancellationException("navigated away")

        val viewModel = createViewModel()
        viewModel.startSharing()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `stopSharing deletes document and clears settings`() = runTest {
        val code = "ABCD1234"
        every { settingsRepository.getShareCode() } returns flowOf(code)
        coJustRun { service.deleteShareDocument(code) }
        coJustRun { settingsRepository.clearShareCode() }
        coJustRun { settingsRepository.setAppMode(AppMode.NONE) }

        val viewModel = createViewModel()
        viewModel.stopSharing()

        coVerify { service.deleteShareDocument(code) }
        coVerify { settingsRepository.clearShareCode() }
        coVerify { settingsRepository.setAppMode(AppMode.NONE) }
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `generateNewCode deletes old document and creates new`() = runTest {
        val oldCode = "OLDCODE1"
        val newCode = "NEWCODE1"
        every { settingsRepository.getShareCode() } returnsMany listOf(
            flowOf(null),    // consumed by init's combine
            flowOf(oldCode), // consumed by generateNewCode for oldCode
            flowOf(newCode), // consumed by generateNewCode for newCode
        )
        coJustRun { service.deleteShareDocument(oldCode) }
        coJustRun { settingsRepository.clearShareCode() }
        coJustRun { generateShareCodeUseCase() }
        coEvery { service.getPartners(newCode) } returns emptyList()

        val viewModel = createViewModel()
        viewModel.generateNewCode()

        coVerify { service.deleteShareDocument(oldCode) }
        coVerify { settingsRepository.clearShareCode() }
        coVerify { generateShareCodeUseCase() }
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `revokePartner calls use case with correct uid`() = runTest {
        val uid = "uid1"
        coJustRun { revokePartnerUseCase(uid) }

        val viewModel = createViewModel()
        viewModel.revokePartner(uid)

        coVerify { revokePartnerUseCase(uid) }
    }
}
