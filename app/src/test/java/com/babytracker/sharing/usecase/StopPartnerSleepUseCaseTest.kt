package com.babytracker.sharing.usecase

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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class StopPartnerSleepUseCaseTest {
    private val submit: SubmitSleepOpUseCase = mockk()
    private val now = Instant.ofEpochMilli(9_000)
    private lateinit var useCase: StopPartnerSleepUseCase

    @BeforeEach
    fun setUp() {
        useCase = StopPartnerSleepUseCase(submit) { now }
    }

    @Test
    fun `builds a STOP op targeting the given clientId with now as end`() = runTest {
        val slot = slot<(String) -> SleepOp>()
        coEvery { submit(capture(slot)) } just Runs

        useCase("active-cid")
        val op = slot.captured("uid")

        assertEquals(SleepOpAction.STOP, op.action)
        assertEquals("active-cid", op.entryClientId)
        assertEquals(9_000L, op.endTimeMs)
    }

    @Test
    fun `blank clientId is rejected`() {
        assertThrows<IllegalArgumentException> { runBlocking { useCase("  ") } }
    }
}
