package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import com.google.firebase.FirebaseException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class FetchPartnerDataUseCaseTest {

    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var useCase: FetchPartnerDataUseCase

    private val shareCode = ShareCode("ABCD1234")
    private val snapshot = ShareSnapshot(
        lastSyncAt = Instant.now(),
        baby = BabySnapshot("Baby", 0L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        useCase = FetchPartnerDataUseCase(sharingRepository, settingsRepository)
        every { settingsRepository.getShareCode() } returns flowOf(shareCode.value)
        coEvery { sharingRepository.signInAnonymously() } returns "uid123"
    }

    @Test
    fun returnsSnapshotWhenPartnerIsConnected() = runTest {
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns true
        coEvery { sharingRepository.fetchSnapshot(shareCode) } returns snapshot

        val result = useCase()

        assertEquals(snapshot, result)
    }

    @Test
    fun throwsPartnerAccessRevokedWhenPartnerIsDisconnected() = runTest {
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns false
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches(shareCode.value) } returns true

        var caught: IllegalStateException? = null
        try {
            useCase()
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught is PartnerAccessRevokedException)
        assertTrue(caught is IllegalStateException)
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches(shareCode.value) }
    }

    @Test
    fun throwsPlainIllegalStateExceptionWhenNoShareCodeStored() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        var caught: IllegalStateException? = null
        try {
            useCase()
        } catch (e: IllegalStateException) {
            caught = e
        }

        assertNotNull(caught)
        assertFalse(caught is PartnerAccessRevokedException)
    }

    @Test
    fun throwsPartnerAccessRevokedWhenShareDocumentMissingAfterConnectivityCheck() = runTest {
        // TOCTOU: isPartnerConnected passes, then primary deletes the share document before
        // fetchSnapshot returns → fetchSnapshot throws ISE("No data in share document …").
        // The use case must classify this as a confirmed revoke, not a transient error.
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns true
        coEvery { sharingRepository.fetchSnapshot(shareCode) } throws
            IllegalStateException("No data in share document ${shareCode.value}")
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches(shareCode.value) } returns true

        var caught: RuntimeException? = null
        try {
            useCase(shareCode)
        } catch (e: RuntimeException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught is PartnerAccessRevokedException)
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches(shareCode.value) }
    }

    @Test
    fun wrapsFirebaseFetchFailureAsPartnerDataFetchException() = runTest {
        coEvery { sharingRepository.isPartnerConnected(shareCode, "uid123") } returns true
        coEvery { sharingRepository.fetchSnapshot(shareCode) } throws FirebaseException("offline")

        var caught: RuntimeException? = null
        try {
            useCase(shareCode)
        } catch (e: RuntimeException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught is PartnerDataFetchException)
    }

    @Test
    fun invokeWithExplicitCodeFetchesThatCodeNotSettings() = runTest {
        // The explicit overload must use the passed code, never re-reading settings — this is what
        // lets the worker cache a snapshot under exactly the code it requested.
        val explicit = ShareCode("ZZ999999")
        coEvery { sharingRepository.isPartnerConnected(explicit, "uid123") } returns true
        coEvery { sharingRepository.fetchSnapshot(explicit) } returns snapshot

        val result = useCase(explicit)

        assertEquals(snapshot, result)
        coVerify { sharingRepository.fetchSnapshot(explicit) }
    }
}
