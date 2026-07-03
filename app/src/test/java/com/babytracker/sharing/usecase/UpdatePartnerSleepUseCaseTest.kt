package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class UpdatePartnerSleepUseCaseTest {
    private val submit: SubmitSleepOpUseCase = mockk()
    private val now = Instant.ofEpochMilli(100_000)
    private val start = now.minusSeconds(600)
    private val end = now.minusSeconds(60)
    private lateinit var useCase: UpdatePartnerSleepUseCase

    @BeforeEach
    fun setUp() {
        useCase = UpdatePartnerSleepUseCase(submit) { now }
    }

    @Test
    fun `builds an UPDATE op with the edited fields`() = runTest {
        val slot = slot<(String) -> SleepOp>()
        coEvery { submit(capture(slot)) } just Runs

        useCase("cid", start, end, SleepType.NAP, "shorter")
        val op = slot.captured("uid")

        assertEquals(SleepOpAction.UPDATE, op.action)
        assertEquals("cid", op.entryClientId)
        assertEquals(start.toEpochMilli(), op.startTimeMs)
        assertEquals(end.toEpochMilli(), op.endTimeMs)
        assertEquals("NAP", op.sleepType)
        assertEquals("shorter", op.notes)
    }

    @Test
    fun `null endTime keeps the session in progress`() = runTest {
        val slot = slot<(String) -> SleepOp>()
        coEvery { submit(capture(slot)) } just Runs

        useCase("cid", start, null, SleepType.NAP, null)

        assertNull(slot.captured("uid").endTimeMs)
    }

    @Test
    fun `future start is rejected`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase("cid", now.plusSeconds(60), null, SleepType.NAP, null) }
        }
    }

    @Test
    fun `end before start is rejected`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase("cid", start, start.minusSeconds(1), SleepType.NAP, null) }
        }
    }

    @Test
    fun `future end is rejected`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase("cid", start, now.plusSeconds(60), SleepType.NAP, null) }
        }
    }

    @Test
    fun `notes longer than the rules bound are rejected`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase("cid", start, end, SleepType.NAP, "x".repeat(PARTNER_NOTES_MAX_LENGTH + 1)) }
        }
    }

    @Test
    fun `blank clientId is rejected`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase("", start, end, SleepType.NAP, null) }
        }
    }
}
