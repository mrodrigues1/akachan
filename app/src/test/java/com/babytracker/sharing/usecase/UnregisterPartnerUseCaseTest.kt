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

class UnregisterPartnerUseCaseTest {

    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: UnregisterPartnerUseCase

    private val shareCode = ShareCode("ABCD1234")

    @BeforeEach
    fun setUp() {
        useCase = UnregisterPartnerUseCase(service, settingsRepository)
    }

    @Test
    fun revokesOwnUidForStoredShareCode() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { service.signInAnonymously() } returns "my-uid"
        coEvery { service.revokePartner(shareCode.value, "my-uid") } just Runs

        useCase()

        coVerify { service.revokePartner(shareCode.value, "my-uid") }
    }

    @Test
    fun noOpWhenShareCodeIsNull() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        useCase()

        coVerify(exactly = 0) { service.revokePartner(any(), any()) }
    }
}
