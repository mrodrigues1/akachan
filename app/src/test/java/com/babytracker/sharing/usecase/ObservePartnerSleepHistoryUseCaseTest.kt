package com.babytracker.sharing.usecase

import com.babytracker.debug.DebugSeedConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.observeSleepOps
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ObservePartnerSleepHistoryUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val now = Instant.ofEpochMilli(100_000)
    private lateinit var useCase: ObservePartnerSleepHistoryUseCase

    @BeforeEach
    fun setUp() {
        // observeSleepOps is a top-level extension function on the service.
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
        useCase = ObservePartnerSleepHistoryUseCase(service, settingsRepository) { now }
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
            id = 1, startTime = 90_000L, endTime = 95_000L, sleepType = "NAP", notes = null,
            clientId = "cid", startedBy = "PARTNER",
        )
        val edit = SleepOp("op-1", SleepOpAction.UPDATE, "cid", "uid", now.toEpochMilli(), 91_000L, 94_000L, "NIGHT_SLEEP", "x")
        coEvery { service.observeSleepOps("CODE1234", "uid") } returns flowOf(listOf(edit))

        val merged = useCase(flowOf(listOf(record))).first()

        assertEquals(1, merged.entries.size)
        assertEquals("NIGHT_SLEEP", merged.entries.first().sleepType)
        assertEquals(94_000L, merged.entries.first().endTime)
        assertEquals(setOf("op-1"), merged.pendingOpIds)
    }

    @Test
    fun `debug placeholder code serves seeded records without hitting Firebase`() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(DebugSeedConfig.PARTNER_SHARE_CODE)
        val record = SleepSnapshot(
            id = 1, startTime = 90_000L, endTime = 95_000L, sleepType = "NAP", notes = null,
            clientId = "cid", startedBy = "OWNER",
        )

        val merged = useCase(flowOf(listOf(record))).first()

        assertEquals(1, merged.entries.size)
        assertEquals("NAP", merged.entries.first().sleepType)
        assertEquals(emptySet<String>(), merged.pendingOpIds)
        coVerify(exactly = 0) { service.signInAnonymously() }
    }
}
