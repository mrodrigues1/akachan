package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectAsPartnerUseCaseTest {

    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: ConnectAsPartnerUseCase

    @BeforeEach
    fun setUp() {
        useCase = ConnectAsPartnerUseCase(service, settingsRepository)
    }

    @Test
    fun invalidFormatCodeThrowsIllegalArgumentException() = runTest {
        var caught: IllegalArgumentException? = null
        try {
            useCase(ShareCode("abc"))
        } catch (e: IllegalArgumentException) {
            caught = e
        }
        assertNotNull(caught)
    }

    @Test
    fun unknownCodeThrowsIllegalStateException() = runTest {
        coEvery { service.signInAnonymously() } returns "uid123"
        coEvery { service.isShareCodeValid("ABCD1234") } returns false

        var caught: IllegalStateException? = null
        try {
            useCase(ShareCode("ABCD1234"))
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertNotNull(caught)
    }

    @Test
    fun validCodeRegistersPartnerAndSetsPartnerMode() = runTest {
        val code = ShareCode("ABCD1234")
        coEvery { service.signInAnonymously() } returns "uid123"
        coEvery { service.isShareCodeValid(code.value) } returns true
        coEvery { service.registerPartner(code.value, "uid123") } just Runs
        coEvery { settingsRepository.setShareCode("ABCD1234") } just Runs
        coEvery { settingsRepository.setAppMode(AppMode.PARTNER) } just Runs

        useCase(code)

        coVerify { service.registerPartner(code.value, "uid123") }
        coVerify { settingsRepository.setShareCode("ABCD1234") }
        coVerify { settingsRepository.setAppMode(AppMode.PARTNER) }
    }
}
