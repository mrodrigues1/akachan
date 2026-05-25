package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapshotReadsTest {

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

    @Test
    fun `snapshot reads return inserted rows`() = runTest {
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 1, endTime = 2, startingSide = "LEFT"),
        )
        db.sleepDao().insertRecord(SleepEntity(startTime = 1, endTime = 2, sleepType = "NAP"))
        val pumpId = db.pumpingDao().insert(PumpingEntity(startTime = 1, endTime = 2, breast = "LEFT"))
        db.milkBagDao().insert(
            MilkBagEntity(collectionDate = 1, volumeMl = 10, sourceSessionId = pumpId, createdAt = 1),
        )

        assertEquals(1, db.breastfeedingDao().getAllSessionsOnce().size)
        assertEquals(1, db.sleepDao().getAllRecordsOnce().size)
        assertEquals(1, db.pumpingDao().getAllSessionsOnce().size)
        assertEquals(1, db.milkBagDao().getAllBagsOnce().size)
    }
}
