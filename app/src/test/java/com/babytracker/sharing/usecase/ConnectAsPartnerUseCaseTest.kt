package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
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

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: ConnectAsPartnerUseCase

    @BeforeEach
    fun setUp() {
        useCase = ConnectAsPartnerUseCase(sharingRepository, settingsRepository)
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
        coEvery { sharingRepository.signInAnonymously() } returns "uid123"
        coEvery { sharingRepository.isShareCodeValid(ShareCode("ABCD1234")) } returns false

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
        coEvery { sharingRepository.signInAnonymously() } returns "uid123"
        coEvery { sharingRepository.isShareCodeValid(code) } returns true
        coEvery { sharingRepository.registerPartner(code, "uid123") } just Runs
        coEvery { settingsRepository.setShareCode("ABCD1234") } just Runs
        coEvery { settingsRepository.setAppMode(AppMode.PARTNER) } just Runs

        useCase(code)

        coVerify { sharingRepository.registerPartner(code, "uid123") }
        coVerify { settingsRepository.setShareCode("ABCD1234") }
        coVerify { settingsRepository.setAppMode(AppMode.PARTNER) }
    }
}
