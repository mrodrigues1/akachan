package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.SharedSleepOpStream
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ObservePartnerActiveSleepUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val sharedSleepOps: SharedSleepOpStream = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private lateinit var useCase: ObservePartnerActiveSleepUseCase

    @BeforeEach
    fun setUp() {
        useCase = ObservePartnerActiveSleepUseCase(service, sharedSleepOps, settingsRepository) { now }
        every { settingsRepository.getShareCode() } returns flowOf("CODE")
        coEvery { service.signInAnonymously() } returns "uid"
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun partnerActive(clientId: String = "p-cid") = SleepSnapshot(
        id = 0, startTime = now.minusSeconds(600).toEpochMilli(), endTime = null,
        sleepType = SleepType.NAP, notes = null, clientId = clientId, startedBy = SleepAuthor.PARTNER,
    )

    private fun ownerActive() = SleepSnapshot(
        id = 1, startTime = now.minusSeconds(600).toEpochMilli(), endTime = null,
        sleepType = SleepType.NAP, notes = null, clientId = "o-cid", startedBy = SleepAuthor.OWNER,
    )

    @Test
    fun `an emitted START op surfaces the optimistic active session`() = runTest {
        val startOp = SleepOp(
            opId = "op-1", action = SleepOpAction.START, entryClientId = "active-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), startTimeMs = now.toEpochMilli(), sleepType = SleepType.NAP,
        )
        every { sharedSleepOps.observe("CODE", "uid") } returns flowOf(listOf(startOp))

        // last(): the ops leg is seeded with an initial empty emission, so the first combined value
        // may not include the pending op yet.
        val result = useCase(flowOf(emptyList())).toList().last()

        assertNotNull(result.active)
        assertEquals("active-cid", result.active?.clientId)
    }

    @Test
    fun `a consumed START overlay is retained until the snapshot shows the session`() = runTest {
        val startOp = SleepOp(
            opId = "op-1", action = SleepOpAction.START, entryClientId = "p-cid",
            authorUid = "uid", createdAtMs = now.toEpochMilli(), startTimeMs = now.toEpochMilli(), sleepType = SleepType.NAP,
        )
        // START op present, then consumed (empty) — BEFORE the snapshot has the session.
        every { sharedSleepOps.observe("CODE", "uid") } returns flowOf(listOf(startOp), emptyList())

        val result = useCase(flowOf(emptyList())).toList().last()

        assertNotNull(result.active)
        assertEquals("p-cid", result.active?.clientId)
    }

    @Test
    fun `a stopped session the primary has not yet published shows as the last completed sleep`() = runTest {
        val t = now.toEpochMilli()
        val startOp = SleepOp(
            opId = "op-start", action = SleepOpAction.START, entryClientId = "c",
            authorUid = "uid", createdAtMs = t - 1_000, startTimeMs = t - 1_000, sleepType = SleepType.NAP,
        )
        val stopOp = SleepOp(
            opId = "op-stop", action = SleepOpAction.STOP, entryClientId = "c",
            authorUid = "uid", createdAtMs = t, endTimeMs = t,
        )
        every { sharedSleepOps.observe("CODE", "uid") } returns flowOf(listOf(startOp, stopOp))

        val result = useCase(flowOf(emptyList())).toList().last()

        assertNull(result.active)
        assertEquals("c", result.lastCompleted?.clientId)
        assertEquals(t, result.lastCompleted?.endTime)
        assertEquals("c", result.mostRecent?.clientId)
    }

    @Test
    fun `canEditActive is true for a partner snapshot session, false for an owner one`() = runTest {
        every { sharedSleepOps.observe("CODE", "uid") } returns flowOf(emptyList())

        assertTrue(useCase(flowOf(listOf(partnerActive()))).first().canEditActive)
        assertFalse(useCase(flowOf(listOf(ownerActive()))).first().canEditActive)
    }

    @Test
    fun `snapshot-backed active session renders even if the op stream never emits`() = runTest {
        // A failing op listener behind SharedSleepOpStream's internal retry never emits — the
        // snapshot leg alone must still produce, or the dashboard tile shows "no sleep" despite a
        // live session in the snapshot.
        every { sharedSleepOps.observe("CODE", "uid") } returns flow { awaitCancellation() }

        val result = useCase(flowOf(listOf(partnerActive("p-cid")))).first()

        assertEquals("p-cid", result.active?.clientId)
        assertTrue(result.canEditActive)
    }

    @Test
    fun `with no share code it resolves from the snapshot alone without signing in`() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        val result = useCase(flowOf(listOf(partnerActive("p-cid")))).first()

        assertEquals("p-cid", result.active?.clientId)
        assertTrue(result.canEditActive)
        coVerify(exactly = 0) { service.signInAnonymously() }
    }
}
