package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BreastfeedingDaoTest {

    private lateinit var database: BabyTrackerDatabase
    private lateinit var dao: BreastfeedingDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.breastfeedingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertSession_andGetAll_returnsInsertedSession() = runTest {
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
    fun getActiveSession_returnsSessionWithNullEndTime() = runTest {
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
    fun updateSession_updatesEndTime() = runTest {
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
}
