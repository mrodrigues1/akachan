package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.ShareCode
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

    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: RevokePartnerUseCase

    private val shareCode = ShareCode("ABCD1234")

    @BeforeEach
    fun setUp() {
        useCase = RevokePartnerUseCase(service, settingsRepository)
    }

    @Test
    fun deletesCorrectPartnerUid() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { service.revokePartner(shareCode.value, "uid-to-remove") } just Runs

        useCase("uid-to-remove")

        coVerify { service.revokePartner(shareCode.value, "uid-to-remove") }
    }

    @Test
    fun noOpWhenShareCodeIsNull() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase("uid-to-remove")

        coVerify(exactly = 0) { service.revokePartner(any(), any()) }
    }

    @Test
    fun unknownUidRevokeSucceedsIdempotently() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { service.revokePartner(shareCode.value, "nonexistent-uid") } just Runs

        useCase("nonexistent-uid")

        coVerify { service.revokePartner(shareCode.value, "nonexistent-uid") }
    }
}
