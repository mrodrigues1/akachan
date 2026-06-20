package com.babytracker.export.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.BottleFeedBackup
import com.babytracker.export.domain.model.DiaperBackup
import com.babytracker.export.domain.model.DoctorVisitBackup
import com.babytracker.export.domain.model.GrowthBackup
import com.babytracker.export.domain.model.MilestoneBackup
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup
import com.babytracker.export.domain.model.VaccineBackup
import com.babytracker.export.domain.model.VisitQuestionBackup
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coVerify
import io.mockk.mockk
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
    private lateinit var scheduler: VaccineReminderScheduler
    private lateinit var doctorScheduler: DoctorVisitReminderScheduler
    private lateinit var importer: BackupImporterImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        scheduler = mockk(relaxed = true)
        doctorScheduler = mockk(relaxed = true)
        importer = BackupImporterImpl(db, scheduler, doctorScheduler)
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
        bottleFeeds: List<BottleFeedBackup> = emptyList(),
        diapers: List<DiaperBackup> = emptyList(),
        vaccines: List<VaccineBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = null, settings = settings(), breastfeeding = emptyList(),
        sleep = sleep, pumping = pumping, milkBags = milkBags, bottleFeeds = bottleFeeds,
        diapers = diapers, vaccines = vaccines,
    )

    @Test
    fun `merges growth and milestones, deduping growth and preserving local milestones`() = runTest {
        val data = BackupData(
            backupFormatVersion = 3, roomSchemaVersion = 12, appVersion = "1.0.0", exportedAt = 0,
            baby = null, settings = settings(), breastfeeding = emptyList(),
            sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(), bottleFeeds = emptyList(),
            growth = listOf(
                GrowthBackup(id = 1, takenAtMs = 1000, type = "WEIGHT", valueCanonical = 5000, notes = null),
                GrowthBackup(id = 2, takenAtMs = 2000, type = "LENGTH", valueCanonical = 600, notes = "n"),
            ),
            milestones = listOf(
                MilestoneBackup(title = "First steps", dateEpochDay = 100, timeMinuteOfDay = 480, note = "steps"),
            ),
        )

        val counts = importer.merge(data)

        assertEquals(2, counts.growthInserted)
        assertEquals(1, counts.milestonesInserted)
        assertEquals(2, db.growthMeasurementDao().getAllOnce().size)
        assertEquals(1, db.milestoneDao().getAllOnce().size)

        // Re-importing the same backup inserts nothing (growth dedup + milestone already present).
        val again = importer.merge(data)
        assertEquals(0, again.growthInserted)
        assertEquals(0, again.milestonesInserted)
    }

    @Test
    fun `imports diapers and dedupes on re-import`() = runTest {
        val data = backup(
            diapers = listOf(
                DiaperBackup(id = 1, timestamp = 100, type = "WET", notes = null, createdAt = 100),
                DiaperBackup(id = 2, timestamp = 200, type = "DIRTY", notes = "blowout", createdAt = 200),
            ),
        )

        val counts = importer.merge(data)
        assertEquals(2, counts.diapersInserted)
        assertEquals(2, db.diaperDao().getAllOnce().size)

        // Re-importing the same backup inserts nothing (identity dedup).
        val again = importer.merge(data)
        assertEquals(0, again.diapersInserted)
        assertEquals(2, db.diaperDao().getAllOnce().size)
    }

    @Test
    fun `imports vaccines, dedupes on re-import, and re-arms reminders`() = runTest {
        val data = backup(
            vaccines = listOf(
                VaccineBackup(id = 1, name = "BCG", doseLabel = "Dose 1", status = "ADMINISTERED", administeredDate = 1_000, createdAt = 1_000),
                VaccineBackup(id = 2, name = "Hep B", doseLabel = null, status = "SCHEDULED", scheduledDate = 9_999, createdAt = 2_000),
            ),
        )

        val counts = importer.merge(data)
        assertEquals(2, counts.vaccinesInserted)
        assertEquals(2, db.vaccineDao().getAllOnce().size)

        // Re-importing the same backup inserts nothing (identity dedup).
        val again = importer.merge(data)
        assertEquals(0, again.vaccinesInserted)
        assertEquals(2, db.vaccineDao().getAllOnce().size)

        // Reminders are re-armed after each import so imported scheduled vaccines get alarms.
        coVerify(exactly = 2) { scheduler.rescheduleAll() }
    }

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
    fun `imports bottle feeds and remaps the linked milk bag FK to the inserted bag`() = runTest {
        val counts = importer.merge(
            backup(
                milkBags = listOf(
                    MilkBagBackup(id = 3, collectionDate = 5, volumeMl = 90, sourceSessionId = null, usedAt = 9, notes = null, createdAt = 5),
                ),
                bottleFeeds = listOf(
                    BottleFeedBackup(id = 1, timestamp = 1_000, volumeMl = 120, type = "BREAST_MILK", linkedMilkBagId = 3, notes = "n", createdAt = 2_000),
                ),
            ),
        )

        assertEquals(1, counts.bottleFeedsInserted)
        val bagId = db.milkBagDao().getAllBagsOnce().single().id
        val feed = db.bottleFeedDao().getAllOnce().single()
        assertEquals(bagId, feed.linkedMilkBagId) // remapped to the real db id, not backup-local 3
        assertEquals(120, feed.volumeMl)
    }

    @Test
    fun `nulls a bottle feed link when the referenced bag is absent`() = runTest {
        val counts = importer.merge(
            backup(
                bottleFeeds = listOf(
                    BottleFeedBackup(id = 1, timestamp = 1_000, volumeMl = 100, type = "FORMULA", linkedMilkBagId = 99, notes = null, createdAt = 2_000),
                ),
            ),
        )

        assertEquals(1, counts.bottleFeedsInserted)
        assertEquals(null, db.bottleFeedDao().getAllOnce().single().linkedMilkBagId)
    }

    @Test
    fun `skips an exact duplicate bottle feed`() = runTest {
        val feed = BottleFeedBackup(id = 1, timestamp = 1_000, volumeMl = 120, type = "FORMULA", linkedMilkBagId = null, notes = "n", createdAt = 2_000)

        val counts = importer.merge(backup(bottleFeeds = listOf(feed, feed.copy(id = 2))))

        assertEquals(1, counts.bottleFeedsInserted)
        assertEquals(1, db.bottleFeedDao().getAllOnce().size)
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

    private fun doctorVisitBackup(
        doctorVisits: List<DoctorVisitBackup> = emptyList(),
        visitQuestions: List<VisitQuestionBackup> = emptyList(),
    ) = BackupData(
        backupFormatVersion = 6, roomSchemaVersion = 15, appVersion = "1.0.0", exportedAt = 0,
        baby = null, settings = settings(), breastfeeding = emptyList(),
        sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(), bottleFeeds = emptyList(),
        doctorVisits = doctorVisits, visitQuestions = visitQuestions,
    )

    @Test
    fun `imports visits and re-links attached questions, deduping on re-import`() = runTest {
        val data = doctorVisitBackup(
            doctorVisits = listOf(
                DoctorVisitBackup(id = 100, date = 5000, providerName = "Dr A", createdAt = 1000),
            ),
            visitQuestions = listOf(
                VisitQuestionBackup(id = 200, text = "attached?", visitId = 100, createdAt = 10),
                VisitQuestionBackup(id = 201, text = "inbox?", visitId = null, createdAt = 20),
            ),
        )

        val counts = importer.merge(data)

        assertEquals(1, counts.doctorVisitsInserted)
        assertEquals(2, counts.visitQuestionsInserted)
        val visitId = db.doctorVisitDao().getAllVisitsOnce().single().id
        val questions = db.doctorVisitDao().getAllQuestionsOnce()
        assertEquals(visitId, questions.single { it.text == "attached?" }.visitId)
        assertEquals(null, questions.single { it.text == "inbox?" }.visitId)
        coVerify { doctorScheduler.rescheduleAll() }

        // Re-importing the same backup inserts nothing (identity dedup).
        val again = importer.merge(data)
        assertEquals(0, again.doctorVisitsInserted)
        assertEquals(0, again.visitQuestionsInserted)
    }

    @Test
    fun `re-links question to an already-existing matching visit instead of the inbox`() = runTest {
        val existingId = db.doctorVisitDao().insertVisit(
            DoctorVisitEntity(date = 5000, providerName = "Dr A", createdAt = 1000),
        )
        val data = doctorVisitBackup(
            doctorVisits = listOf(
                DoctorVisitBackup(id = 100, date = 5000, providerName = "Dr A", createdAt = 1000),
            ),
            visitQuestions = listOf(
                VisitQuestionBackup(id = 200, text = "q?", visitId = 100, createdAt = 10),
            ),
        )

        val counts = importer.merge(data)

        assertEquals(0, counts.doctorVisitsInserted) // deduped against the existing visit
        assertEquals(1, counts.visitQuestionsInserted)
        val question = db.doctorVisitDao().getAllQuestionsOnce().single { it.text == "q?" }
        assertEquals(existingId, question.visitId) // re-linked to the existing visit, not the inbox
    }
}
