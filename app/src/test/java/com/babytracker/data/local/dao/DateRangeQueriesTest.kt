package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateRangeQueriesTest {

    private lateinit var db: BabyTrackerDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    // Range semantics: a completed record is included if it OVERLAPS [start,end] at all
    // (start <= end AND end >= start), so an overnight sleep that begins before the window and
    // ends inside it is not silently dropped from a doctor's summary.

    @Test
    fun `breastfeeding between includes overlapping completed rows and excludes others`() = runTest {
        // fully inside
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 100, endTime = 200, startingSide = "LEFT"),
        )
        // starts before window, ends inside -> INCLUDED (overlap)
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = -50, endTime = 50, startingSide = "RIGHT"),
        )
        // in-progress (endTime null) -> excluded
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 150, endTime = null, startingSide = "RIGHT"),
        )
        // fully after window -> excluded
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 5000, endTime = 6000, startingSide = "LEFT"),
        )
        val result = db.breastfeedingDao().getCompletedSessionsBetween(0, 1000)
        assertEquals(2, result.size)
        assertEquals(listOf(-50L, 100L), result.map { it.startTime })
    }

    @Test
    fun `sleep between includes overnight record crossing the start boundary`() = runTest {
        // overnight: starts before window, ends inside -> INCLUDED
        db.sleepDao().insertRecord(SleepEntity(startTime = -100, endTime = 300, sleepType = "NIGHT_SLEEP"))
        // fully inside
        db.sleepDao().insertRecord(SleepEntity(startTime = 400, endTime = 500, sleepType = "NAP"))
        // in-progress -> excluded
        db.sleepDao().insertRecord(SleepEntity(startTime = 150, endTime = null, sleepType = "NAP"))
        // fully after -> excluded
        db.sleepDao().insertRecord(SleepEntity(startTime = 5000, endTime = 6000, sleepType = "NAP"))
        val result = db.sleepDao().getCompletedRecordsBetween(0, 1000)
        assertEquals(2, result.size)
        assertEquals(listOf(-100L, 400L), result.map { it.startTime })
    }
}
