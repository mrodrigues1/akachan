package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PumpingRepositoryImplTest {
    private lateinit var dao: PumpingDao
    private lateinit var repository: PumpingRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = PumpingRepositoryImpl(dao)
    }

    @Test
    fun `getRecentSessionsFlow maps rows to domain sessions in dao order`() = runTest {
        every { dao.getRecentSessionsFlow(2) } returns flowOf(
            listOf(
                PumpingEntity(id = 2, startTime = 2_000L, endTime = 2_500L, breast = "RIGHT", volumeMl = 90),
                PumpingEntity(id = 1, startTime = 1_000L, endTime = 1_500L, breast = "LEFT", volumeMl = 60),
            ),
        )

        repository.getRecentSessionsFlow(2).test {
            val sessions = awaitItem()
            assertEquals(listOf(2L, 1L), sessions.map { it.id })
            assertEquals(PumpingBreast.RIGHT, sessions[0].breast)
            assertEquals(Instant.ofEpochMilli(1_500L), sessions[1].endTime)
            assertEquals(60, sessions[1].volumeMl)
            awaitComplete()
        }
    }

    @Test
    fun `getById returns null when dao has no row`() = runTest {
        coEvery { dao.getById(5L) } returns null

        assertNull(repository.getById(5L))
    }

    @Test
    fun `getById maps entity to domain`() = runTest {
        coEvery { dao.getById(7L) } returns
            PumpingEntity(id = 7, startTime = 1_000L, breast = "LEFT", volumeMl = 80, notes = "morning")

        val session = repository.getById(7L)

        assertEquals(7L, session?.id)
        assertEquals(Instant.ofEpochMilli(1_000L), session?.startTime)
        assertEquals(PumpingBreast.LEFT, session?.breast)
        assertEquals(80, session?.volumeMl)
        assertEquals("morning", session?.notes)
    }

    @Test
    fun `update forwards toEntity fields to dao`() = runTest {
        val captured = slot<PumpingEntity>()
        coEvery { dao.update(capture(captured)) } returns Unit

        repository.update(
            PumpingSession(
                id = 9,
                startTime = Instant.ofEpochMilli(1_000L),
                endTime = null,
                breast = PumpingBreast.BOTH,
                volumeMl = 110,
                notes = "evening",
                pausedAt = Instant.ofEpochMilli(1_200L),
                pausedDurationMs = 300L,
            ),
        )

        assertEquals(9L, captured.captured.id)
        assertEquals(1_000L, captured.captured.startTime)
        assertNull(captured.captured.endTime)
        assertEquals("BOTH", captured.captured.breast)
        assertEquals(110, captured.captured.volumeMl)
        assertEquals("evening", captured.captured.notes)
        assertEquals(1_200L, captured.captured.pausedAt)
        assertEquals(300L, captured.captured.pausedDurationMs)
    }

    @Test
    fun `getActiveSession maps entity to domain`() = runTest {
        every { dao.getActiveSession() } returns flowOf(
            PumpingEntity(id = 7, startTime = 1_000L, breast = "LEFT"),
        )

        repository.getActiveSession().test {
            val session = awaitItem()
            assertEquals(7L, session?.id)
            assertEquals(PumpingBreast.LEFT, session?.breast)
            awaitComplete()
        }
    }

    @Test
    fun `insert forwards toEntity to dao`() = runTest {
        val captured = slot<PumpingEntity>()
        coEvery { dao.insert(capture(captured)) } returns 42L

        val id = repository.insert(
            PumpingSession(
                startTime = Instant.ofEpochMilli(2_000L),
                breast = PumpingBreast.BOTH,
            ),
        )

        assertEquals(42L, id)
        assertEquals(2_000L, captured.captured.startTime)
        assertEquals("BOTH", captured.captured.breast)
    }

    @Test
    fun `delete forwards toEntity to dao`() = runTest {
        val session = PumpingSession(
            id = 9,
            startTime = Instant.ofEpochMilli(1_000L),
            breast = PumpingBreast.RIGHT,
        )
        repository.delete(session)
        coVerify { dao.delete(match { it.id == 9L }) }
    }

    @Test
    fun `updateEndTimeIfActive forwards stop fields to dao`() = runTest {
        coEvery {
            dao.updateEndTimeIfActive(
                id = 9,
                endTime = 3_000L,
                volumeMl = 120,
                pausedDurationMs = 500L,
            )
        } returns 1
        val session = PumpingSession(
            id = 9,
            startTime = Instant.ofEpochMilli(1_000L),
            endTime = Instant.ofEpochMilli(3_000L),
            breast = PumpingBreast.RIGHT,
            volumeMl = 120,
            pausedDurationMs = 500L,
        )

        val result = repository.updateEndTimeIfActive(session)

        assertEquals(true, result)
    }
}
