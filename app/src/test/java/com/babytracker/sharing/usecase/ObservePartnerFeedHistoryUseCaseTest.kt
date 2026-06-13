package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.repository.SharingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class ObservePartnerFeedHistoryUseCaseTest {
    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private lateinit var useCase: ObservePartnerFeedHistoryUseCase

    @BeforeEach
    fun setUp() {
        useCase = ObservePartnerFeedHistoryUseCase(sharingRepository, settingsRepository)
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { sharingRepository.signInAnonymously() } returns "partner-uid"
    }

    @Test
    fun `permission denied listener clears partner state and throws revoked`() = runTest {
        val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        every { sharingRepository.observeOwnFeedOps(any(), "partner-uid") } returns flow {
            throw denied
        }
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true

        assertThrows<PartnerAccessRevokedException> {
            runBlocking {
                useCase(emptyList()).first()
            }
        }

        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `non-revoked listener failure wraps as PartnerDataFetchException`() = runTest {
        every { sharingRepository.observeOwnFeedOps(any(), "partner-uid") } returns flow {
            throw IOException("network down")
        }

        assertThrows<PartnerDataFetchException> {
            runBlocking {
                useCase(emptyList()).first()
            }
        }
    }
}
