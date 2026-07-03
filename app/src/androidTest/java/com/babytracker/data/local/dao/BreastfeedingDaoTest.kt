package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BreastfeedingDaoTest {

    private lateinit var database: BabyTrackerDatabase
    private lateinit var dao: BreastfeedingDao

    @BeforeEach
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.breastfeedingDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertSessionAndGetAllReturnsInsertedSession() = runTest {
        val entity = BreastfeedingEntity(
            startTime = System.currentTimeMillis(),
            startingSide = "LEFT"
        )

        dao.insertSession(entity)
        val sessions = dao.getAllSessions().first()

        assertEquals(1, sessions.size)
        assertEquals("LEFT", sessions[0].startingSide)
    }

    @Test
    fun getActiveSessionReturnsSessionWithNullEndTime() = runTest {
        val active = BreastfeedingEntity(
            startTime = System.currentTimeMillis(),
            startingSide = "LEFT"
        )
        val completed = BreastfeedingEntity(
            startTime = System.currentTimeMillis() - 60000,
            endTime = System.currentTimeMillis(),
            startingSide = "RIGHT"
        )

        dao.insertSession(active)
        dao.insertSession(completed)

        val result = dao.getActiveSession().first()
        assertEquals("LEFT", result?.startingSide)
        assertNull(result?.endTime)
    }

    @Test
    fun observeLatestCompletedSessionReturnsNullWhenNoCompletedSessionExists() = runTest {
        dao.insertSession(
            BreastfeedingEntity(startTime = System.currentTimeMillis(), startingSide = "LEFT")
        )

        assertNull(dao.observeLatestCompletedSession().first())
    }

    @Test
    fun observeLatestCompletedSessionPicksLatestEndTimeAndIgnoresActive() = runTest {
        val now = System.currentTimeMillis()
        // Newer completed session started earlier but ended later — end_time must win.
        dao.insertSession(
            BreastfeedingEntity(startTime = now - 10000, endTime = now - 5000, startingSide = "LEFT")
        )
        dao.insertSession(
            BreastfeedingEntity(startTime = now - 20000, endTime = now - 1000, startingSide = "RIGHT")
        )
        dao.insertSession(
            BreastfeedingEntity(startTime = now, startingSide = "LEFT")
        )

        val latest = dao.observeLatestCompletedSession().first()
        assertEquals("RIGHT", latest?.startingSide)
        assertEquals(now - 1000, latest?.endTime)
    }

    @Test
    fun updateSessionUpdatesEndTime() = runTest {
        val entity = BreastfeedingEntity(
            startTime = System.currentTimeMillis(),
            startingSide = "LEFT"
        )

        val id = dao.insertSession(entity)
        val endTime = System.currentTimeMillis() + 60000
        dao.updateSession(entity.copy(id = id, endTime = endTime))

        val sessions = dao.getAllSessions().first()
        assertEquals(endTime, sessions[0].endTime)
    }

    @Test
    fun startSessionIfNoneInsertsAndReturnsIdWhenNoActiveSession() = runTest {
        val entity = BreastfeedingEntity(startTime = System.currentTimeMillis(), startingSide = "LEFT")

        val id = dao.startSessionIfNone(entity)

        assertNotNull(id)
        assertTrue(id!! > 0)
        assertNotNull(dao.getActiveSessionOnce())
    }

    @Test
    fun startSessionIfNoneReturnsNullAndDoesNotInsertWhenActiveExists() = runTest {
        val existing = BreastfeedingEntity(startTime = System.currentTimeMillis(), startingSide = "RIGHT")
        dao.insertSession(existing)

        val id = dao.startSessionIfNone(
            BreastfeedingEntity(startTime = System.currentTimeMillis() + 1000, startingSide = "LEFT")
        )

        assertNull(id)
        assertEquals(1, dao.getAllSessions().first().size)
    }

    @Test
    fun startSessionIfNoneReturnsNullOnConstraintLoss() = runTest {
        val existing = BreastfeedingEntity(startTime = System.currentTimeMillis(), startingSide = "RIGHT")
        dao.insertSession(existing)

        val id = dao.startSessionIfNone(
            BreastfeedingEntity(startTime = System.currentTimeMillis() + 500, startingSide = "LEFT")
        )

        assertNull(id)
        assertNotNull(dao.getActiveSessionOnce())
    }

    @Test
    fun stopActiveSessionReturnsTrueAndSetsEndTime() = runTest {
        val entity = BreastfeedingEntity(startTime = System.currentTimeMillis(), startingSide = "LEFT")
        dao.insertSession(entity)
        val endTime = System.currentTimeMillis() + 60_000L

        val stopped = dao.stopActiveSession(endTime)

        assertTrue(stopped)
        assertNull(dao.getActiveSessionOnce())
        assertEquals(endTime, dao.getAllSessions().first()[0].endTime)
    }

    @Test
    fun stopActiveSessionFoldsOpenPauseIntoPausedDuration() = runTest {
        val start = System.currentTimeMillis()
        val pausedAt = start + 60_000L
        dao.insertSession(
            BreastfeedingEntity(
                startTime = start,
                startingSide = "LEFT",
                pausedAt = pausedAt,
                pausedDurationMs = 30_000L,
            )
        )
        val endTime = pausedAt + 120_000L

        val stopped = dao.stopActiveSession(endTime)

        assertTrue(stopped)
        val session = dao.getAllSessions().first()[0]
        assertEquals(endTime, session.endTime)
        assertNull(session.pausedAt)
        assertEquals(30_000L + 120_000L, session.pausedDurationMs)
    }

    @Test
    fun stopActiveSessionReturnsFalseWhenNoActiveSession() = runTest {
        val stopped = dao.stopActiveSession(System.currentTimeMillis())

        assertFalse(stopped)
    }
}
