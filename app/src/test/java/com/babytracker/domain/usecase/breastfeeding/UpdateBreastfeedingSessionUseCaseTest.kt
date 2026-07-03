package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: UpdateBreastfeedingSessionUseCase

    private val now = Instant.parse("2026-05-15T12:00:00Z")
    private val originalStart = Instant.parse("2026-05-15T09:00:00Z")
    private val originalEnd = Instant.parse("2026-05-15T09:30:00Z")
    private val originalSwitch = Instant.parse("2026-05-15T09:15:00Z")

    private val session = BreastfeedingSession(
        id = 7L,
        startTime = originalStart,
        endTime = originalEnd,
        startingSide = BreastSide.LEFT,
        switchTime = originalSwitch,
        pausedAt = null,
        pausedDurationMs = 0L,
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = UpdateBreastfeedingSessionUseCase(repository, Clock.fixed(now, ZoneOffset.UTC))
    }

    @Test
    fun invokeUpdatesStartAndEndTime() = runTest {
        val newStart = Instant.parse("2026-05-15T10:00:00Z")
        val newEnd = Instant.parse("2026-05-15T10:45:00Z")
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session, newStart, newEnd)

        assertEquals(newStart, slot.captured.startTime)
        assertEquals(newEnd, slot.captured.endTime)
    }

    @Test
    fun invokePreservesIdAndStartingSide() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(
            session,
            newStart = Instant.parse("2026-05-15T10:00:00Z"),
            newEnd = Instant.parse("2026-05-15T10:45:00Z"),
        )

        assertEquals(7L, slot.captured.id)
        assertEquals(BreastSide.LEFT, slot.captured.startingSide)
    }

    @Test
    fun invokeKeepsSwitchTimeWhenInsideNewRange() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        // new range still contains originalSwitch (09:15)
        useCase(
            session,
            newStart = Instant.parse("2026-05-15T09:00:00Z"),
            newEnd = Instant.parse("2026-05-15T09:45:00Z"),
        )

        assertEquals(originalSwitch, slot.captured.switchTime)
    }

    @Test
    fun invokeNullsSwitchTimeWhenOutsideNewRange() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        // shift range past the original switch time
        useCase(
            session,
            newStart = Instant.parse("2026-05-15T09:20:00Z"),
            newEnd = Instant.parse("2026-05-15T09:50:00Z"),
        )

        assertNull(slot.captured.switchTime)
    }

    @Test
    fun invokeNullsPausedAtWhenOutsideNewRange() = runTest {
        val pausedSession = session.copy(
            pausedAt = Instant.parse("2026-05-15T09:10:00Z"),
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(
            pausedSession,
            newStart = Instant.parse("2026-05-15T09:20:00Z"),
            newEnd = Instant.parse("2026-05-15T09:50:00Z"),
        )

        assertNull(slot.captured.pausedAt)
    }

    @Test
    fun invokeAllowsNullEndForInProgressSession() = runTest {
        val inProgress = session.copy(endTime = null, switchTime = null)
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        val newStart = Instant.parse("2026-05-15T10:00:00Z")
        useCase(inProgress, newStart, null)

        assertEquals(newStart, slot.captured.startTime)
        assertNull(slot.captured.endTime)
    }

    @Test
    fun invokeThrowsWhenEndBeforeStart() = runTest {
        val newStart = Instant.parse("2026-05-15T10:00:00Z")
        val newEnd = Instant.parse("2026-05-15T09:30:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(session, newStart, newEnd) }
        }
    }

    @Test
    fun invokeThrowsWhenEndInFuture() = runTest {
        val newStart = Instant.parse("2026-05-15T11:00:00Z")
        val newEnd = now.plusSeconds(60)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(session, newStart, newEnd) }
        }
    }

    @Test
    fun invokeCallsUpdateExactlyOnceOnValidInput() = runTest {
        coJustRun { repository.updateSession(any()) }

        useCase(
            session,
            newStart = Instant.parse("2026-05-15T10:00:00Z"),
            newEnd = Instant.parse("2026-05-15T10:30:00Z"),
        )

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }

    @Test
    fun invokeClosingInProgressPausedSessionFoldsPauseIntoPausedDurationMs() = runTest {
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:20:00Z"),
            pausedDurationMs = 30_000L,
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        // Close 10 minutes after the pause began.
        val newEnd = Instant.parse("2026-05-15T09:30:00Z")
        useCase(inProgressPaused, inProgressPaused.startTime, newEnd)

        assertNull(slot.captured.pausedAt)
        assertEquals(30_000L + 10 * 60_000L, slot.captured.pausedDurationMs)
        assertEquals(newEnd, slot.captured.endTime)
    }

    @Test
    fun invokeClosingPausedSessionRejectsWhenPauseExceedsRange() = runTest {
        // Original paused at 09:20, original pausedDurationMs 0.
        // New range is 09:00 to 09:21 (1 minute total). Fold adds 60s of pause from 09:20 to 09:21.
        // sessionMs = 60_000 * 21 = 1_260_000, foldedPause = 60_000 — still valid.
        // But if we squeeze further so fold exceeds session, validation must reject.
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:00:30Z"),
            pausedDurationMs = 0L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    inProgressPaused,
                    newStart = Instant.parse("2026-05-15T09:00:00Z"),
                    newEnd = Instant.parse("2026-05-15T09:00:15Z"),
                )
            }
        }
    }

    @Test
    fun invokeStayingInProgressKeepsPausedAtWhenInRange() = runTest {
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:10:00Z"),
            pausedDurationMs = 5_000L,
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(inProgressPaused, newStart = inProgressPaused.startTime, newEnd = null)

        assertEquals(Instant.parse("2026-05-15T09:10:00Z"), slot.captured.pausedAt)
        assertEquals(5_000L, slot.captured.pausedDurationMs)
        assertNull(slot.captured.endTime)
    }

    @Test
    fun invokeStayingInProgressNullsPausedAtWhenOutsideNewRange() = runTest {
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:10:00Z"),
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(
            inProgressPaused,
            newStart = Instant.parse("2026-05-15T09:15:00Z"),
            newEnd = null,
        )

        assertNull(slot.captured.pausedAt)
    }
}
