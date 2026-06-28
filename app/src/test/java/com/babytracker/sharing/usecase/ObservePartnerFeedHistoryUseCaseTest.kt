package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.Instant

class ObservePartnerFeedHistoryUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val now = Instant.ofEpochMilli(100_000)
    private lateinit var useCase: ObservePartnerFeedHistoryUseCase

    private fun feed(clientId: String) = BottleFeedSnapshot(
        timestamp = 5_000L, volumeMl = 90, type = "FORMULA", clientId = clientId, author = "PARTNER",
    )

    private fun createOp(clientId: String) = FeedOp(
        opId = "op-$clientId", action = FeedOpAction.CREATE, entryClientId = clientId, authorUid = "uid",
        createdAtMs = now.toEpochMilli(), timestampMs = 5_000L, volumeMl = 90, type = "FORMULA",
    )

    @BeforeEach
    fun setUp() {
        useCase = ObservePartnerFeedHistoryUseCase(service, settingsRepository) { now }
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { service.signInAnonymously() } returns "uid"
    }

    @Test
    fun `overlays the partner's own pending feed op on the snapshot`() = runTest {
        every { service.observeFeedOps("CODE1234", "uid") } returns flowOf(listOf(createOp("c1")))

        val merged = useCase(flowOf(emptyList())).first()

        assertEquals(listOf("c1"), merged.entries.map { it.clientId })
    }

    @Test
    fun `op-delete delivered before the snapshot update keeps the entry, then converges`() = runTest {
        // Op present, then deleted (empty), while the snapshot is stale until the last emission.
        every { service.observeFeedOps("CODE1234", "uid") } returns flowOf(listOf(createOp("c1")), emptyList(), emptyList())
        val snapshotFeeds = flowOf(emptyList(), emptyList(), listOf(feed("c1")))

        var lastEntries: List<String> = emptyList()
        useCase(snapshotFeeds).collectIndexed { _, merged -> lastEntries = merged.entries.map { it.clientId } }

        // After op-delete + stale snapshot the entry is retained; the final fresh snapshot converges to it.
        assertEquals(listOf("c1"), lastEntries)
    }

    @Test
    fun `permission denied clears partner state and throws revoked`() = runTest {
        val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        every { service.observeFeedOps("CODE1234", "uid") } returns flow { throw denied }
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true

        assertThrows<PartnerAccessRevokedException> {
            runBlocking { useCase(flowOf(emptyList())).first() }
        }
        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `non-revoked failure wraps as PartnerDataFetchException`() = runTest {
        every { service.observeFeedOps("CODE1234", "uid") } returns flow { throw IOException("network down") }

        assertThrows<PartnerDataFetchException> {
            runBlocking { useCase(flowOf(emptyList())).first() }
        }
    }
}
