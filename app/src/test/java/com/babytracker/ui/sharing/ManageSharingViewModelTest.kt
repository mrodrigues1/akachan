package com.babytracker.ui.sharing

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.GenerateShareCodeUseCase
import com.babytracker.sharing.usecase.RevokePartnerUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ManageSharingViewModelTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sharingRepository: SharingRepository
    private lateinit var generateShareCodeUseCase: GenerateShareCodeUseCase
    private lateinit var revokePartnerUseCase: RevokePartnerUseCase

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settingsRepository = mockk()
        sharingRepository = mockk()
        generateShareCodeUseCase = mockk()
        revokePartnerUseCase = mockk()
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)
        every { settingsRepository.getShareCode() } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ManageSharingViewModel(
        settingsRepository = settingsRepository,
        sharingRepository = sharingRepository,
        generateShareCodeUseCase = generateShareCodeUseCase,
        revokePartnerUseCase = revokePartnerUseCase,
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
        coEvery { sharingRepository.getPartners(ShareCode(code)) } returns partners

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
        coEvery { sharingRepository.getPartners(ShareCode(code)) } returns partners

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
    fun `stopSharing deletes document and clears settings`() = runTest {
        val code = "ABCD1234"
        every { settingsRepository.getShareCode() } returns flowOf(code)
        coJustRun { sharingRepository.deleteShareDocument(ShareCode(code)) }
        coJustRun { settingsRepository.clearShareCode() }
        coJustRun { settingsRepository.setAppMode(AppMode.NONE) }

        val viewModel = createViewModel()
        viewModel.stopSharing()

        coVerify { sharingRepository.deleteShareDocument(ShareCode(code)) }
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
        coJustRun { sharingRepository.deleteShareDocument(ShareCode(oldCode)) }
        coJustRun { settingsRepository.clearShareCode() }
        coJustRun { generateShareCodeUseCase() }
        coEvery { sharingRepository.getPartners(ShareCode(newCode)) } returns emptyList()

        val viewModel = createViewModel()
        viewModel.generateNewCode()

        coVerify { sharingRepository.deleteShareDocument(ShareCode(oldCode)) }
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
