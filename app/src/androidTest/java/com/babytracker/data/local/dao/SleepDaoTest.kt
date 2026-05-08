package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SleepDaoTest {

    private lateinit var database: BabyTrackerDatabase
    private lateinit var dao: SleepDao

    @BeforeEach
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.sleepDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertRecord_returnsGeneratedId() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")

        val id = dao.insertRecord(entity)

        assertTrue(id > 0)
    }

    @Test
    fun insertRecord_andGetAll_returnsRecord() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")

        dao.insertRecord(entity)
        val records = dao.getAllRecords().first()

        assertEquals(1, records.size)
        assertEquals("NAP", records[0].sleepType)
    }

    @Test
    fun getAllRecords_orderedByStartTimeDesc() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NIGHT_SLEEP"))

        val records = dao.getAllRecords().first()

        assertEquals("NIGHT_SLEEP", records[0].sleepType)
        assertEquals("NAP", records[1].sleepType)
    }

    @Test
    fun getAllRecords_emptyDb_returnsEmptyList() = runTest {
        val records = dao.getAllRecords().first()

        assertEquals(0, records.size)
    }

    @Test
    fun updateRecord_updatesEndTime() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")
        val id = dao.insertRecord(entity)
        val endTime = System.currentTimeMillis() + 60_000L

        dao.updateRecord(entity.copy(id = id, endTime = endTime))
        val records = dao.getAllRecords().first()

        assertEquals(endTime, records[0].endTime)
    }

    @Test
    fun updateRecord_keepingNullEndTime_remainsNull() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")
        val id = dao.insertRecord(entity)

        dao.updateRecord(entity.copy(id = id, notes = "updated"))
        val records = dao.getAllRecords().first()

        assertNull(records[0].endTime)
    }

    @Test
    fun getCompletedRecordsSince_excludesInProgress() = runTest {
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NAP"))

        val results = dao.getCompletedRecordsSince(0L)

        assertEquals(0, results.size)
    }

    @Test
    fun getCompletedRecordsSince_excludesBeforeCutoff() = runTest {
        dao.insertRecord(SleepEntity(startTime = 500L, endTime = 600L, sleepType = "NAP"))

        val results = dao.getCompletedRecordsSince(1000L)

        assertEquals(0, results.size)
    }

    @Test
    fun getCompletedRecordsSince_includesExactCutoffMs() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, endTime = 2000L, sleepType = "NAP"))

        val results = dao.getCompletedRecordsSince(1000L)

        assertEquals(1, results.size)
    }

    @Test
    fun getCompletedRecordsSince_emptyDb_returnsEmpty() = runTest {
        val results = dao.getCompletedRecordsSince(0L)

        assertEquals(0, results.size)
    }

    @Test
    fun getRecentRecords_respectsLimit() = runTest {
        repeat(5) { i ->
            dao.insertRecord(SleepEntity(startTime = i.toLong() * 1000L, sleepType = "NAP"))
        }

        val results = dao.getRecentRecords(3)

        assertEquals(3, results.size)
    }

    @Test
    fun getRecentRecords_limitExceedsCount_returnsAll() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NAP"))

        val results = dao.getRecentRecords(10)

        assertEquals(2, results.size)
    }

    @Test
    fun getRecentRecords_orderedByStartTimeDesc() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NIGHT_SLEEP"))

        val results = dao.getRecentRecords(2)

        assertEquals("NIGHT_SLEEP", results[0].sleepType)
        assertEquals("NAP", results[1].sleepType)
    }
}
