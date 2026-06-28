package com.babytracker.sharing.usecase

import app.cash.turbine.test
import com.babytracker.debug.DebugPartnerSnapshotBuilder
import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.ConnectionEmission
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.SnapshotEmission
import com.babytracker.sharing.data.firebase.observePartnerConnected
import com.babytracker.sharing.data.firebase.observeSnapshot
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ObservePartnerDataUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val debugBuilder: DebugPartnerSnapshotBuilder = mockk()
    private lateinit var useCase: ObservePartnerDataUseCase

    private val snapshot = ShareSnapshot(
        lastSyncAt = java.time.Instant.EPOCH,
        baby = BabySnapshot("Baby", 0L, emptyList()),
        sessions = emptyList(),
        sleepRecords = emptyList(),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
        useCase = ObservePartnerDataUseCase(service, settingsRepository, Lazy { debugBuilder })
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { service.signInAnonymously() } returns "uid"
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `emits the snapshot when present and connected from cache`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(snapshot, fromCache = true))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = true))

        assertEquals(snapshot, useCase().first()) // cached data must display offline-first
    }

    @Test
    fun `server-confirmed disconnect clears state and throws revoked`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(false, fromCache = false))

        assertThrows<PartnerAccessRevokedException> { useCase().first() }
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `server-confirmed missing snapshot clears state and throws revoked`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(null, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = false))

        assertThrows<PartnerAccessRevokedException> { useCase().first() }
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `cache-origin missing snapshot does NOT clear or throw and later server snapshot is emitted`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns
            flowOf(SnapshotEmission(null, fromCache = true), SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = false))

        useCase().test {
            assertEquals(snapshot, awaitItem())
            awaitComplete()
        }
        coVerify(exactly = 0) { settingsRepository.clearPartnerStateIfShareCodeMatches(any()) }
    }

    @Test
    fun `cache-origin disconnect is ignored until the server confirms connection`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flowOf(SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected("CODE1234", "uid") } returns
            flowOf(ConnectionEmission(false, fromCache = true), ConnectionEmission(true, fromCache = false))

        useCase().test {
            assertEquals(snapshot, awaitItem())
            awaitComplete()
        }
        coVerify(exactly = 0) { settingsRepository.clearPartnerStateIfShareCodeMatches(any()) }
    }

    @Test
    fun `debug placeholder code serves the seeded snapshot without signing in`() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(DebugSeedConfig.PARTNER_SHARE_CODE)
        coEvery { debugBuilder.build() } returns snapshot

        assertEquals(snapshot, useCase().first())
        coVerify(exactly = 0) { service.signInAnonymously() }
    }

    @Test
    fun `firebase listener error is wrapped as PartnerDataFetchException`() = runTest {
        every { service.observeSnapshot("CODE1234") } returns flow {
            throw FirebaseFirestoreException("boom", FirebaseFirestoreException.Code.UNAVAILABLE)
        }
        every { service.observePartnerConnected("CODE1234", "uid") } returns flowOf(ConnectionEmission(true, fromCache = false))

        assertThrows<PartnerDataFetchException> { useCase().first() }
    }
}
