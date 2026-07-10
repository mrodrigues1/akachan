package com.babytracker.sharing.usecase

import com.babytracker.debug.DebugSeedConfig
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ObservePartnerSleepHistoryUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val sharedSleepOps: SharedSleepOpStream = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val now = Instant.ofEpochMilli(100_000)
    private lateinit var useCase: ObservePartnerSleepHistoryUseCase

    @BeforeEach
    fun setUp() {
        useCase = ObservePartnerSleepHistoryUseCase(service, sharedSleepOps, settingsRepository) { now }
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { service.signInAnonymously() } returns "uid"
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `maps snapshot records overlaid with the partner's pending edits`() = runTest {
        val record = SleepSnapshot(
            id = 1, startTime = 90_000L, endTime = 95_000L, sleepType = SleepType.NAP, notes = null,
            clientId = "cid", startedBy = SleepAuthor.PARTNER,
        )
        val edit = SleepOp("op-1", SleepOpAction.UPDATE, "cid", "uid", now.toEpochMilli(), 91_000L, 94_000L, SleepType.NIGHT_SLEEP, "x")
        every { sharedSleepOps.observe("CODE1234", "uid") } returns flowOf(listOf(edit))

        // last(): the ops leg is seeded with an initial empty emission, so the first combined value
        // may not include the pending edit yet.
        val merged = useCase(flowOf(listOf(record))).toList().last()

        assertEquals(1, merged.entries.size)
        assertEquals(SleepType.NIGHT_SLEEP, merged.entries.first().sleepType)
        assertEquals(94_000L, merged.entries.first().endTime)
        assertEquals(setOf("op-1"), merged.pendingOpIds)
    }

    @Test
    fun `snapshot records render even if the op stream never emits`() = runTest {
        // A failing op listener behind SharedSleepOpStream's internal retry never emits — the
        // snapshot leg alone must still produce, or the history screen stalls on its loading spinner.
        val record = SleepSnapshot(
            id = 1, startTime = 90_000L, endTime = 95_000L, sleepType = SleepType.NAP, notes = null,
            clientId = "cid", startedBy = SleepAuthor.PARTNER,
        )
        every { sharedSleepOps.observe("CODE1234", "uid") } returns flow { awaitCancellation() }

        val merged = useCase(flowOf(listOf(record))).first()

        assertEquals(1, merged.entries.size)
        assertEquals("cid", merged.entries.first().clientId)
        assertEquals(emptySet<String>(), merged.pendingOpIds)
    }

    @Test
    fun `debug placeholder code serves seeded records without hitting Firebase`() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(DebugSeedConfig.PARTNER_SHARE_CODE)
        val record = SleepSnapshot(
            id = 1, startTime = 90_000L, endTime = 95_000L, sleepType = SleepType.NAP, notes = null,
            clientId = "cid", startedBy = SleepAuthor.OWNER,
        )

        val merged = useCase(flowOf(listOf(record))).first()

        assertEquals(1, merged.entries.size)
        assertEquals(SleepType.NAP, merged.entries.first().sleepType)
        assertEquals(emptySet<String>(), merged.pendingOpIds)
        coVerify(exactly = 0) { service.signInAnonymously() }
    }
}
