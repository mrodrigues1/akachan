package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class StartPartnerSleepUseCaseTest {
    private val submit: SubmitSleepOpUseCase = mockk()
    private val now = Instant.ofEpochMilli(5_000)
    private lateinit var useCase: StartPartnerSleepUseCase

    @BeforeEach
    fun setUp() {
        useCase = StartPartnerSleepUseCase(submit) { now }
    }

    @Test
    fun `builds a START op with a fresh clientId and now times`() = runTest {
        val slot = slot<(String) -> SleepOp>()
        coEvery { submit(capture(slot)) } just Runs

        val clientId = useCase(SleepType.NIGHT_SLEEP)
        val op = slot.captured("uid")

        assertEquals(SleepOpAction.START, op.action)
        assertEquals(clientId, op.entryClientId)
        assertTrue(clientId.isNotBlank())
        assertEquals("uid", op.authorUid)
        assertEquals(5_000L, op.startTimeMs)
        assertEquals(5_000L, op.createdAtMs)
        assertEquals(SleepType.NIGHT_SLEEP, op.sleepType)
    }
}
