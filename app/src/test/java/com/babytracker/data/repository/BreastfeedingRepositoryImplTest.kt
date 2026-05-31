package com.babytracker.data.repository

import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import android.database.sqlite.SQLiteConstraintException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedingRepositoryImplTest {

    private lateinit var dao: BreastfeedingDao
    private lateinit var repository: BreastfeedingRepositoryImpl

    private val startEpoch = 1_700_000_000_000L
    private val endEpoch = 1_700_000_060_000L

    private val entity = BreastfeedingEntity(
        id = 1L,
        startTime = startEpoch,
        endTime = endEpoch,
        startingSide = "LEFT"
    )

    @BeforeEach
    fun setUp() {
        dao = mockk()
        repository = BreastfeedingRepositoryImpl(dao)
    }

    @Test
    fun getAllSessionsMapsEntitiesToDomain() = runTest {
        every { dao.getAllSessions() } returns flowOf(listOf(entity))

        val sessions = repository.getAllSessions().first()

        assertEquals(1, sessions.size)
        assertEquals(1L, sessions[0].id)
        assertEquals(BreastSide.LEFT, sessions[0].startingSide)
        assertEquals(Instant.ofEpochMilli(startEpoch), sessions[0].startTime)
        assertEquals(Instant.ofEpochMilli(endEpoch), sessions[0].endTime)
    }

    @Test
    fun getAllSessionsEmptyListEmitsEmpty() = runTest {
        every { dao.getAllSessions() } returns flowOf(emptyList())

        val sessions = repository.getAllSessions().first()

        assertEquals(0, sessions.size)
    }

    @Test
    fun getActiveSessionNullEntityReturnsNull() = runTest {
        every { dao.getActiveSession() } returns flowOf(null)

        val result = repository.getActiveSession().first()

        assertNull(result)
    }

    @Test
    fun getActiveSessionEntityMapsToDomain() = runTest {
        val activeEntity = entity.copy(endTime = null)
        every { dao.getActiveSession() } returns flowOf(activeEntity)

        val result = repository.getActiveSession().first()

        assertEquals(1L, result?.id)
        assertEquals(BreastSide.LEFT, result?.startingSide)
        assertNull(result?.endTime)
    }

    @Test
    fun getLastSessionNullEntityReturnsNull() = runTest {
        coEvery { dao.getLastSession() } returns null

        val result = repository.getLastSession()

        assertNull(result)
    }

    @Test
    fun getLastSessionEntityMapsToDomain() = runTest {
        coEvery { dao.getLastSession() } returns entity

        val result = repository.getLastSession()

        assertEquals(1L, result?.id)
        assertEquals(BreastSide.LEFT, result?.startingSide)
    }

    @Test
    fun getRecentSessionsMapsEntities() = runTest {
        coEvery { dao.getRecentSessions(any()) } returns listOf(entity)

        val results = repository.getRecentSessions(1)

        assertEquals(1, results.size)
        assertEquals(BreastSide.LEFT, results[0].startingSide)
    }

    @Test
    fun insertSessionConvertsToEntityAndDelegates() = runTest {
        val session = BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startEpoch),
            startingSide = BreastSide.RIGHT
        )
        val slot = slot<BreastfeedingEntity>()
        coEvery { dao.insertSession(capture(slot)) } returns 5L

        repository.insertSession(session)

        assertEquals("RIGHT", slot.captured.startingSide)
        assertEquals(startEpoch, slot.captured.startTime)
    }

    @Test
    fun insertSessionReturnsIdFromDao() = runTest {
        val session = BreastfeedingSession(startTime = Instant.now(), startingSide = BreastSide.LEFT)
        coEvery { dao.insertSession(any()) } returns 88L

        val id = repository.insertSession(session)

        assertEquals(88L, id)
    }

    @Test
    fun updateSessionConvertsNullableFieldsToEpochMs() = runTest {
        val switchEpoch = startEpoch + 30_000L
        val pauseEpoch = startEpoch + 45_000L
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            startingSide = BreastSide.LEFT,
            switchTime = Instant.ofEpochMilli(switchEpoch),
            pausedAt = Instant.ofEpochMilli(pauseEpoch),
            pausedDurationMs = 5_000L
        )
        val slot = slot<BreastfeedingEntity>()
        coJustRun { dao.updateSession(capture(slot)) }

        repository.updateSession(session)

        assertEquals(endEpoch, slot.captured.endTime)
        assertEquals(switchEpoch, slot.captured.switchTime)
        assertEquals(pauseEpoch, slot.captured.pausedAt)
        assertEquals(5_000L, slot.captured.pausedDurationMs)
    }

    @Test
    fun insertSessionActiveConstraintViolationReturnsExistingId() = runTest {
        val activeSession = BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startEpoch),
            startingSide = BreastSide.LEFT
        )
        val existingEntity = entity.copy(endTime = null, id = 42L)
        coEvery { dao.insertSession(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")
        coEvery { dao.getActiveSessionOnce() } returns existingEntity

        val id = repository.insertSession(activeSession)

        assertEquals(42L, id)
    }

    @Test
    fun insertSessionActiveConstraintViolationNoActiveRowRethrows() = runTest {
        val activeSession = BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startEpoch),
            startingSide = BreastSide.LEFT
        )
        coEvery { dao.insertSession(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")
        coEvery { dao.getActiveSessionOnce() } returns null

        var thrown: SQLiteConstraintException? = null
        try {
            repository.insertSession(activeSession)
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun insertSessionCompletedConstraintViolationAlwaysRethrows() = runTest {
        val completedSession = BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            startingSide = BreastSide.LEFT
        )
        coEvery { dao.insertSession(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")

        var thrown: SQLiteConstraintException? = null
        try {
            repository.insertSession(completedSession)
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun startSessionIfNoneConvertsToEntityAndReturnsId() = runTest {
        val session = BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startEpoch),
            startingSide = BreastSide.LEFT
        )
        val slot = slot<BreastfeedingEntity>()
        coEvery { dao.startSessionIfNone(capture(slot)) } returns 7L

        val result = repository.startSessionIfNone(session)

        assertEquals(7L, result)
        assertEquals("LEFT", slot.captured.startingSide)
        assertEquals(startEpoch, slot.captured.startTime)
    }

    @Test
    fun startSessionIfNoneReturnsNullWhenDaoReturnsNull() = runTest {
        val session = BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startEpoch),
            startingSide = BreastSide.RIGHT
        )
        coEvery { dao.startSessionIfNone(any()) } returns null

        val result = repository.startSessionIfNone(session)

        assertNull(result)
    }

    @Test
    fun stopActiveSessionPassesEpochMillisToDao() = runTest {
        val endInstant = Instant.ofEpochMilli(endEpoch)
        val slot = slot<Long>()
        coEvery { dao.stopActiveSession(capture(slot)) } returns true

        repository.stopActiveSession(endInstant)

        assertEquals(endEpoch, slot.captured)
    }

    @Test
    fun stopActiveSessionReturnsTrueWhenDaoReturnsTrue() = runTest {
        coEvery { dao.stopActiveSession(any()) } returns true

        val result = repository.stopActiveSession(Instant.ofEpochMilli(endEpoch))

        assertEquals(true, result)
    }

    @Test
    fun stopActiveSessionReturnsFalseWhenDaoReturnsFalse() = runTest {
        coEvery { dao.stopActiveSession(any()) } returns false

        val result = repository.stopActiveSession(Instant.ofEpochMilli(endEpoch))

        assertEquals(false, result)
    }
}
