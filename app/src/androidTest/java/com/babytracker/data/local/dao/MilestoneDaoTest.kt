package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.MilestoneEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MilestoneDaoTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: MilestoneDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.milestoneDao()
    }

    @After
    fun tearDown() = db.close()

    private fun moment(title: String, day: Long, minute: Int? = null) =
        MilestoneEntity(title = title, dateEpochDay = day, timeMinuteOfDay = minute)

    @Test
    fun insert_thenGetById_returnsTheMoment() = runTest {
        val id = dao.insert(moment("First smile", day = 100, minute = 480))

        val loaded = dao.getById(id).first()
        assertEquals("First smile", loaded?.title)
        assertEquals(480, loaded?.timeMinuteOfDay)
    }

    @Test
    fun getAll_ordersNewestFirst_withTimedBeforeTimeless() = runTest {
        dao.insert(moment("Older", day = 100))
        dao.insert(moment("Newer timeless", day = 200, minute = null))
        dao.insert(moment("Newer timed", day = 200, minute = 600))

        val titles = dao.getAll().first().map { it.title }
        assertEquals(listOf("Newer timed", "Newer timeless", "Older"), titles)
    }

    @Test
    fun update_modifiesExistingRow() = runTest {
        val id = dao.insert(moment("Draft", day = 100))

        dao.update(MilestoneEntity(id = id, title = "Final", dateEpochDay = 100))

        assertEquals("Final", dao.getById(id).first()?.title)
    }

    @Test
    fun deleteById_removesOnlyThatRow() = runTest {
        val keep = dao.insert(moment("Keep", day = 100))
        val drop = dao.insert(moment("Drop", day = 150))

        dao.deleteById(drop)

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("Keep", all.first().title)
        assertNull(dao.getById(drop).first())
        assertEquals(keep, all.first().id)
    }
}
