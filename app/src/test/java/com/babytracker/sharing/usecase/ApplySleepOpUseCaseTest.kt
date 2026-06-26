package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ApplySleepOpUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: ApplySleepOpUseCase

    private val now = Instant.ofEpochMilli(1_000_000)
    private val past = now.minusSeconds(600)

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = ApplySleepOpUseCase(repository) { now }
    }

    private fun startOp(clientId: String = "c1", startMs: Long = past.toEpochMilli(), type: String? = "NAP") =
        SleepOp("op", SleepOpAction.START, clientId, "uid", now.toEpochMilli(), startTimeMs = startMs, sleepType = type)

    private fun stopOp(clientId: String = "c1", endMs: Long = now.toEpochMilli()) =
        SleepOp("op", SleepOpAction.STOP, clientId, "uid", now.toEpochMilli(), endTimeMs = endMs)

    private fun updateOp(clientId: String = "c1", startMs: Long = past.toEpochMilli(), type: String? = "NIGHT_SLEEP") =
        SleepOp("op", SleepOpAction.UPDATE, clientId, "uid", now.toEpochMilli(), startTimeMs = startMs, sleepType = type)

    private fun record(clientId: String = "c1", author: SleepAuthor = SleepAuthor.PARTNER, endTime: Instant? = null) =
        SleepRecord(id = 5, startTime = past, endTime = endTime, sleepType = SleepType.NAP, clientId = clientId, startedBy = author)

    @Test
    fun `start with no active session inserts a PARTNER record and notifies started`() = runTest {
        coEvery { repository.getByClientId("c1") } returns null
        coEvery { repository.getActiveRecord() } returns null
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 1L

        val result = useCase(startOp())

        assertTrue(result.roomChanged)
        assertEquals(SleepNotifyKind.STARTED, result.notification?.kind)
        assertEquals("c1", slot.captured.clientId)
        assertEquals(SleepAuthor.PARTNER, slot.captured.startedBy)
        assertNull(slot.captured.endTime)
    }

    @Test
    fun `start is idempotent when the session already exists`() = runTest {
        coEvery { repository.getByClientId("c1") } returns record()

        val result = useCase(startOp())

        assertFalse(result.roomChanged)
        assertNull(result.notification)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }

    @Test
    fun `start converges (no insert) when another session is already active`() = runTest {
        coEvery { repository.getByClientId("c1") } returns null
        coEvery { repository.getActiveRecord() } returns record(clientId = "other")

        val result = useCase(startOp())

        assertFalse(result.roomChanged)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }

    @Test
    fun `start in the future is dropped`() = runTest {
        val result = useCase(startOp(startMs = now.plusSeconds(60).toEpochMilli()))
        assertFalse(result.roomChanged)
    }

    @Test
    fun `start with invalid sleepType is dropped`() = runTest {
        val result = useCase(startOp(type = "siesta"))
        assertFalse(result.roomChanged)
    }

    @Test
    fun `stop ends the session regardless of who started it`() = runTest {
        coEvery { repository.getByClientId("c1") } returns record(author = SleepAuthor.OWNER, endTime = null)
        val slot = slot<SleepRecord>()
        coEvery { repository.updateRecord(capture(slot)) } returns Unit

        val result = useCase(stopOp())

        assertTrue(result.roomChanged)
        assertEquals(SleepNotifyKind.STOPPED, result.notification?.kind)
        assertEquals(now, slot.captured.endTime)
    }

    @Test
    fun `stop clamps an end before start up to the start time`() = runTest {
        coEvery { repository.getByClientId("c1") } returns record(endTime = null)
        val slot = slot<SleepRecord>()
        coEvery { repository.updateRecord(capture(slot)) } returns Unit

        useCase(stopOp(endMs = past.minusSeconds(60).toEpochMilli()))

        assertEquals(past, slot.captured.endTime)
    }

    @Test
    fun `stop on an already-ended session is a no-op`() = runTest {
        coEvery { repository.getByClientId("c1") } returns record(endTime = now)

        val result = useCase(stopOp())

        assertFalse(result.roomChanged)
        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }

    @Test
    fun `stop with a missing target is dropped`() = runTest {
        coEvery { repository.getByClientId("c1") } returns null
        assertFalse(useCase(stopOp()).roomChanged)
    }

    @Test
    fun `update edits a PARTNER session`() = runTest {
        coEvery { repository.getByClientId("c1") } returns record(author = SleepAuthor.PARTNER)
        val slot = slot<SleepRecord>()
        coEvery { repository.updateRecord(capture(slot)) } returns Unit

        val result = useCase(updateOp())

        assertTrue(result.roomChanged)
        assertEquals(SleepNotifyKind.EDITED, result.notification?.kind)
        assertEquals(SleepType.NIGHT_SLEEP, slot.captured.sleepType)
    }

    @Test
    fun `update targeting an OWNER session is dropped`() = runTest {
        coEvery { repository.getByClientId("c1") } returns record(author = SleepAuthor.OWNER)

        val result = useCase(updateOp())

        assertFalse(result.roomChanged)
        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }

    @Test
    fun `update with a missing target is dropped`() = runTest {
        coEvery { repository.getByClientId("c1") } returns null
        assertFalse(useCase(updateOp()).roomChanged)
    }

    @Test
    fun `update with end before start is dropped`() = runTest {
        val op = updateOp().copy(endTimeMs = past.minusSeconds(60).toEpochMilli())
        assertFalse(useCase(op).roomChanged)
    }
}
