package com.babytracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant

// Regression for #758. Editing a sleep entry must keep the stored clientId. UpdateSleepEntryUseCase
// builds a brand-new SleepRecord whose clientId now defaults to a FRESH random UUID (the domain
// default matches the entity). Identity continuity across an edit therefore depends entirely on
// SleepDao.updateRecordPreservingIdentity restoring the stored id; if that guard ever regressed,
// every edit would silently mint a new clientId and break partner-merge identity.
@RunWith(RobolectricTestRunner::class)
class SleepEditPreservesClientIdTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var repository: SleepRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SleepRepositoryImpl(db.sleepDao(), db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `editing a sleep entry keeps the stored clientId`() = runTest {
        val start = Instant.ofEpochMilli(1_700_000_000_000L)
        val id = repository.insertRecord(
            SleepRecord(startTime = start, endTime = start.plusSeconds(600), sleepType = SleepType.NAP),
        )
        val originalClientId = repository.getLatestRecord()!!.clientId
        assertTrue("insert should mint a real clientId", originalClientId.isNotBlank())

        // Edit via the production call site, which constructs a SleepRecord with a fresh-UUID default.
        UpdateSleepEntryUseCase(repository, Clock.systemDefaultZone())(
            id = id,
            startTime = start,
            endTime = start.plusSeconds(1200),
            type = SleepType.NIGHT_SLEEP,
        )

        val edited = repository.getLatestRecord()!!
        assertEquals(id, edited.id)
        assertEquals(SleepType.NIGHT_SLEEP, edited.sleepType)
        assertEquals(start.plusSeconds(1200), edited.endTime)
        // The stored clientId is unchanged despite the edit's fresh-UUID default.
        assertEquals(originalClientId, edited.clientId)
    }
}
