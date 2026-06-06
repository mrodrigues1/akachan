package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.repository.BabyEventRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LogBabyEventUseCaseTest {

    private lateinit var repository: BabyEventRepository
    private val fixedNow = Instant.parse("2026-06-06T10:00:00Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private lateinit var useCase: LogBabyEventUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = LogBabyEventUseCase(repository, fixedClock)
    }

    @Test
    fun `invoke logs SICK event to repository with correct type and timestamp`() = runTest {
        coEvery { repository.logEvent(any()) } returns Unit

        useCase(BabyEventType.SICK)

        val slot = slot<BabyEvent>()
        coVerify { repository.logEvent(capture(slot)) }
        val saved = slot.captured
        assertEquals(BabyEventType.SICK, saved.type)
        assertEquals(fixedNow, saved.timestamp)
        assertEquals(fixedNow, saved.createdAt)
        assertNull(saved.intensity)
        assertNull(saved.notes)
    }

    @Test
    fun `invoke logs SLEEPY_CUE event`() = runTest {
        coEvery { repository.logEvent(any()) } returns Unit

        useCase(BabyEventType.SLEEPY_CUE)

        val slot = slot<BabyEvent>()
        coVerify { repository.logEvent(capture(slot)) }
        assertEquals(BabyEventType.SLEEPY_CUE, slot.captured.type)
    }

    @Test
    fun `invoke logs TEETHING event`() = runTest {
        coEvery { repository.logEvent(any()) } returns Unit

        useCase(BabyEventType.TEETHING)

        val slot = slot<BabyEvent>()
        coVerify { repository.logEvent(capture(slot)) }
        assertEquals(BabyEventType.TEETHING, slot.captured.type)
    }

    @Test
    fun `BabyEventType isDisruption true for SICK TEETHING TRAVEL`() {
        assertTrue(BabyEventType.SICK.isDisruption)
        assertTrue(BabyEventType.TEETHING.isDisruption)
        assertTrue(BabyEventType.TRAVEL.isDisruption)
    }

    @Test
    fun `BabyEventType isDisruption false for SLEEPY_CUE HUNGER_CUE FUSSY`() {
        assertFalse(BabyEventType.SLEEPY_CUE.isDisruption)
        assertFalse(BabyEventType.HUNGER_CUE.isDisruption)
        assertFalse(BabyEventType.FUSSY.isDisruption)
    }
}
