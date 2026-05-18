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
    fun insertRecordReturnsGeneratedId() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")

        val id = dao.insertRecord(entity)

        assertTrue(id > 0)
    }

    @Test
    fun insertRecordAndGetAllReturnsRecord() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")

        dao.insertRecord(entity)
        val records = dao.getAllRecords().first()

        assertEquals(1, records.size)
        assertEquals("NAP", records[0].sleepType)
    }

    @Test
    fun getAllRecordsOrderedByStartTimeDesc() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NIGHT_SLEEP"))

        val records = dao.getAllRecords().first()

        assertEquals("NIGHT_SLEEP", records[0].sleepType)
        assertEquals("NAP", records[1].sleepType)
    }

    @Test
    fun getAllRecordsEmptyDbReturnsEmptyList() = runTest {
        val records = dao.getAllRecords().first()

        assertEquals(0, records.size)
    }

    @Test
    fun updateRecordUpdatesEndTime() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")
        val id = dao.insertRecord(entity)
        val endTime = System.currentTimeMillis() + 60_000L

        dao.updateRecord(entity.copy(id = id, endTime = endTime))
        val records = dao.getAllRecords().first()

        assertEquals(endTime, records[0].endTime)
    }

    @Test
    fun updateRecordKeepingNullEndTimeRemainsNull() = runTest {
        val entity = SleepEntity(startTime = System.currentTimeMillis(), sleepType = "NAP")
        val id = dao.insertRecord(entity)

        dao.updateRecord(entity.copy(id = id, notes = "updated"))
        val records = dao.getAllRecords().first()

        assertNull(records[0].endTime)
    }

    @Test
    fun getCompletedRecordsSinceExcludesInProgress() = runTest {
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NAP"))

        val results = dao.getCompletedRecordsSince(0L)

        assertEquals(0, results.size)
    }

    @Test
    fun getCompletedRecordsSinceExcludesBeforeCutoff() = runTest {
        dao.insertRecord(SleepEntity(startTime = 500L, endTime = 600L, sleepType = "NAP"))

        val results = dao.getCompletedRecordsSince(1000L)

        assertEquals(0, results.size)
    }

    @Test
    fun getCompletedRecordsSinceIncludesExactCutoffMs() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, endTime = 2000L, sleepType = "NAP"))

        val results = dao.getCompletedRecordsSince(1000L)

        assertEquals(1, results.size)
    }

    @Test
    fun getCompletedRecordsSinceEmptyDbReturnsEmpty() = runTest {
        val results = dao.getCompletedRecordsSince(0L)

        assertEquals(0, results.size)
    }

    @Test
    fun getRecentRecordsRespectsLimit() = runTest {
        repeat(5) { i ->
            dao.insertRecord(SleepEntity(startTime = i.toLong() * 1000L, sleepType = "NAP"))
        }

        val results = dao.getRecentRecords(3)

        assertEquals(3, results.size)
    }

    @Test
    fun getRecentRecordsLimitExceedsCountReturnsAll() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NAP"))

        val results = dao.getRecentRecords(10)

        assertEquals(2, results.size)
    }

    @Test
    fun getRecentRecordsOrderedByStartTimeDesc() = runTest {
        dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NIGHT_SLEEP"))

        val results = dao.getRecentRecords(2)

        assertEquals("NIGHT_SLEEP", results[0].sleepType)
        assertEquals("NAP", results[1].sleepType)
    }

    @Test
    fun deleteRecordRemovesItFromAllRecords() = runTest {
        val id = dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))

        dao.deleteRecord(id)
        val records = dao.getAllRecords().first()

        assertEquals(0, records.size)
    }

    @Test
    fun deleteRecordLeavesOtherRecordsIntact() = runTest {
        val idToDelete = dao.insertRecord(SleepEntity(startTime = 1000L, sleepType = "NAP"))
        dao.insertRecord(SleepEntity(startTime = 2000L, sleepType = "NIGHT_SLEEP"))

        dao.deleteRecord(idToDelete)
        val records = dao.getAllRecords().first()

        assertEquals(1, records.size)
        assertEquals("NIGHT_SLEEP", records[0].sleepType)
    }
}
