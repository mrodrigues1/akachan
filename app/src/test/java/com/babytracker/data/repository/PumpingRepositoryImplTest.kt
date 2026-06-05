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
