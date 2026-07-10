package com.babytracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant

// Regression for #775. Save is read-validate-write; before SleepRepository.inTransaction existed,
// two concurrent saves could each validate against the same pre-write snapshot and both persist,
// storing overlapping records. With the transaction, SQLite serializes the writers, so the second
// save re-reads the first's committed row and must reject with OVERLAP.
@RunWith(RobolectricTestRunner::class)
class SleepSaveTransactionalityTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var useCase: SaveSleepEntryUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        useCase = SaveSleepEntryUseCase(SleepRepositoryImpl(db.sleepDao(), db), Clock.systemDefaultZone())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `concurrent overlapping saves persist exactly one record`() = runTest {
        val start = Instant.ofEpochMilli(1_700_000_000_000L)
        val end = start.plusSeconds(1800)

        val results = (1..2).map {
            async(Dispatchers.IO) { runCatching { useCase(start, end, SleepType.NAP) } }
        }.awaitAll()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.sleepDao().getAllRecordsOnce().size)
    }
}
