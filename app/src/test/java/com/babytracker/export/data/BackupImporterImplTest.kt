package com.babytracker.export.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupImporterImplTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var importer: BackupImporterImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        importer = BackupImporterImpl(db)
    }

    @After
    fun tearDown() = db.close()

    private fun settings() = SettingsBackup(
        "SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60,
    )

    private fun backup(
        sleep: List<SleepBackup> = emptyList(),
        pumping: List<PumpingBackup> = emptyList(),
        milkBags: List<MilkBagBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = null, settings = settings(), breastfeeding = emptyList(),
        sleep = sleep, pumping = pumping, milkBags = milkBags,
    )

    @Test
    fun `skips an exact duplicate but keeps a row differing in any field`() = runTest {
        db.sleepDao().insertRecord(SleepEntity(startTime = 10, endTime = 20, sleepType = "NAP", notes = "a"))
        val counts = importer.merge(
            backup(
                sleep = listOf(
                    SleepBackup(99, 10, 20, "NAP", "a"),  // exact duplicate -> skipped
                    SleepBackup(98, 10, 20, "NAP", "b"),  // differs in notes -> kept
                ),
            ),
        )
        assertEquals(1, counts.sleepInserted)
        assertEquals(2, db.sleepDao().getAllRecordsOnce().size)
    }

    @Test
    fun `sleep import repairs missing timezone on otherwise identical record`() = runTest {
        db.sleepDao().insertRecord(
            SleepEntity(
                startTime = 10,
                endTime = 20,
                sleepType = "NAP",
                notes = "a",
                timezoneId = null,
            ),
        )

        val counts = importer.merge(
            backup(
                sleep = listOf(
                    SleepBackup(99, 10, 20, "NAP", "a", timezoneId = "UTC"),
                ),
            ),
        )

        val records = db.sleepDao().getAllRecordsOnce()
        assertEquals(0, counts.sleepInserted)
        assertEquals(1, records.size)
        assertEquals("UTC", records.single().timezoneId)
    }

    @Test
    fun `sleep import preserves local timezone when backup timezone conflicts`() = runTest {
        db.sleepDao().insertRecord(
            SleepEntity(
                startTime = 10,
                endTime = 20,
                sleepType = "NAP",
                notes = "a",
                timezoneId = "America/Sao_Paulo",
            ),
        )

        val counts = importer.merge(
            backup(
                sleep = listOf(
                    SleepBackup(99, 10, 20, "NAP", "a", timezoneId = "UTC"),
                ),
            ),
        )

        val records = db.sleepDao().getAllRecordsOnce()
        assertEquals(0, counts.sleepInserted)
        assertEquals(1, records.size)
        assertEquals("America/Sao_Paulo", records.single().timezoneId)
    }

    @Test
    fun `sleep import canonicalizes legacy sleep type labels`() = runTest {
        val counts = importer.merge(
            backup(
                sleep = listOf(
                    SleepBackup(99, 10, 20, "Nap", "a", timezoneId = "UTC"),
                    SleepBackup(98, 30, 40, "Night Sleep", "b", timezoneId = "UTC"),
                ),
            ),
        )

        val records = db.sleepDao().getAllRecordsOnce()
        assertEquals(2, counts.sleepInserted)
        assertEquals(setOf("NAP", "NIGHT_SLEEP"), records.map { it.sleepType }.toSet())
    }

    @Test
    fun `remaps milk bag FK to a newly inserted pumping row`() = runTest {
        val counts = importer.merge(
            backup(
                pumping = listOf(PumpingBackup(id = 7, startTime = 1, endTime = 2, breast = "LEFT", volumeMl = 100, notes = null, pausedAt = null, pausedDurationMs = 0)),
                milkBags = listOf(MilkBagBackup(id = 3, collectionDate = 5, volumeMl = 90, sourceSessionId = 7, usedAt = null, notes = null, createdAt = 5)),
            ),
        )
        assertEquals(1, counts.pumpingInserted)
        assertEquals(1, counts.milkBagsInserted)
        val pumpId = db.pumpingDao().getAllSessionsOnce().single().id
        val bag = db.milkBagDao().getAllBagsOnce().single()
        assertEquals(pumpId, bag.sourceSessionId)  // remapped to the real db id, not backup-local 7
    }

    @Test
    fun `milk bag referencing a deduped pumping row attaches to existing db id`() = runTest {
        // Pumping row already present in DB, identical to the backup's pumping row.
        val existingId = db.pumpingDao().insert(
            PumpingEntity(startTime = 1, endTime = 2, breast = "LEFT", volumeMl = 100),
        )
        val counts = importer.merge(
            backup(
                pumping = listOf(PumpingBackup(id = 7, startTime = 1, endTime = 2, breast = "LEFT", volumeMl = 100, notes = null, pausedAt = null, pausedDurationMs = 0)),
                milkBags = listOf(MilkBagBackup(id = 3, collectionDate = 5, volumeMl = 90, sourceSessionId = 7, usedAt = null, notes = null, createdAt = 5)),
            ),
        )
        assertEquals(0, counts.pumpingInserted) // deduped against existing
        assertEquals(1, counts.milkBagsInserted)
        assertEquals(existingId, db.milkBagDao().getAllBagsOnce().single().sourceSessionId)
    }

    @Test
    fun `dedupe repairs an unlinked existing bag's source without inserting a duplicate`() = runTest {
        // Existing bag identical on all natural fields, but UNLINKED (source null).
        db.milkBagDao().insert(
            com.babytracker.data.local.entity.MilkBagEntity(
                collectionDate = 5, volumeMl = 90, sourceSessionId = null,
                usedAt = null, notes = null, createdAt = 5,
            ),
        )
        val counts = importer.merge(
            backup(
                pumping = listOf(PumpingBackup(7, 1, 2, "LEFT", 100, null, null, 0)),
                milkBags = listOf(MilkBagBackup(3, 5, 90, sourceSessionId = 7, usedAt = null, notes = null, createdAt = 5)),
            ),
        )
        // No second inventory bag (no double-counting), but the existing bag's link is repaired.
        assertEquals(0, counts.milkBagsInserted)
        val bags = db.milkBagDao().getAllBagsOnce()
        assertEquals(1, bags.size)
        val pumpId = db.pumpingDao().getAllSessionsOnce().single().id
        assertEquals(pumpId, bags.single().sourceSessionId)
    }
}
