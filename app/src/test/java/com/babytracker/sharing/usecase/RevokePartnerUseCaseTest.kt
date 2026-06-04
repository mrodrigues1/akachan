package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RevokePartnerUseCaseTest {

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: RevokePartnerUseCase

    private val shareCode = ShareCode("ABCD1234")

    @BeforeEach
    fun setUp() {
        useCase = RevokePartnerUseCase(sharingRepository, settingsRepository)
    }

    @Test
    fun deletesCorrectPartnerUid() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { sharingRepository.revokePartner(shareCode, "uid-to-remove") } just Runs

        useCase("uid-to-remove")

        coVerify { sharingRepository.revokePartner(shareCode, "uid-to-remove") }
    }

    @Test
    fun noOpWhenShareCodeIsNull() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase("uid-to-remove")

        coVerify(exactly = 0) { sharingRepository.revokePartner(any(), any()) }
    }

    @Test
    fun unknownUidRevokeSucceedsIdempotently() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { sharingRepository.revokePartner(shareCode, "nonexistent-uid") } just Runs

        useCase("nonexistent-uid")

        coVerify { sharingRepository.revokePartner(shareCode, "nonexistent-uid") }
    }
}
