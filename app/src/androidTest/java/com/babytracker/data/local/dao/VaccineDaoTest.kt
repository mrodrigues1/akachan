package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaccineDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: VaccineDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.vaccineDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeAllOrdersByEffectiveDateDesc() = runTest {
        // effective date = COALESCE(administered, scheduled, created)
        dao.insert(VaccineEntity(name = "Future", status = "SCHEDULED", scheduledDate = 5_000, createdAt = 1))
        dao.insert(VaccineEntity(name = "Given", status = "ADMINISTERED", administeredDate = 200, createdAt = 1))
        dao.insert(VaccineEntity(name = "Created", status = "ADMINISTERED", createdAt = 3_000))

        val all = dao.observeAll().first()
        assertEquals(listOf("Future", "Created", "Given"), all.map { it.name })
    }

    @Test
    fun upcomingAndFutureFilter() = runTest {
        // past scheduled (overdue), future scheduled, administered
        dao.insert(VaccineEntity(name = "Overdue", status = "SCHEDULED", scheduledDate = 100, createdAt = 1))
        dao.insert(VaccineEntity(name = "Future", status = "SCHEDULED", scheduledDate = 5_000, createdAt = 1))
        dao.insert(VaccineEntity(name = "Given", status = "ADMINISTERED", administeredDate = 200, createdAt = 1))

        val upcoming = dao.observeUpcoming().first()
        assertEquals(listOf("Overdue", "Future"), upcoming.map { it.name }) // both scheduled, asc

        val future = dao.getScheduledFutureAfter(1_000)
        assertEquals(listOf("Future"), future.map { it.name }) // only > 1000 and SCHEDULED
    }

    @Test
    fun markAdministeredAndDelete() = runTest {
        val id = dao.insert(VaccineEntity(name = "BCG", status = "SCHEDULED", scheduledDate = 100, createdAt = 1))
        dao.update(VaccineEntity(id = id, name = "BCG", status = "ADMINISTERED", administeredDate = 150, createdAt = 1))
        assertEquals("ADMINISTERED", dao.getById(id)!!.status)
        assertEquals(0, dao.observeUpcoming().first().size)
        assertEquals(1, dao.deleteById(id))
    }

    @Test
    fun getAllOnceOrdersByCreatedAsc() = runTest {
        dao.insert(VaccineEntity(name = "Second", status = "ADMINISTERED", createdAt = 200))
        dao.insert(VaccineEntity(name = "First", status = "ADMINISTERED", createdAt = 100))
        assertEquals(listOf("First", "Second"), dao.getAllOnce().map { it.name })
    }
}
