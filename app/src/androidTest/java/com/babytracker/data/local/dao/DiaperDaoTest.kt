package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.DiaperEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaperDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: DiaperDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.diaperDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertsAndObservesNewestFirst() = runTest {
        dao.insert(DiaperEntity(timestamp = 100, type = "WET", createdAt = 100))
        dao.insert(DiaperEntity(timestamp = 300, type = "DIRTY", createdAt = 300))
        dao.insert(DiaperEntity(timestamp = 200, type = "BOTH", createdAt = 200))

        val all = dao.observeAll().first()
        assertEquals(listOf(300L, 200L, 100L), all.map { it.timestamp })
        assertEquals(300L, dao.observeLatest().first()!!.timestamp)
        assertEquals(1, dao.getBetween(150, 250).size)
        assertEquals(listOf(100L, 200L, 300L), dao.getAllOnce().map { it.timestamp })
    }

    @Test
    fun updateAndDelete() = runTest {
        val id = dao.insert(DiaperEntity(timestamp = 100, type = "WET", createdAt = 100))
        dao.update(DiaperEntity(id = id, timestamp = 100, type = "BOTH", createdAt = 100))
        assertEquals("BOTH", dao.getById(id)!!.type)
        assertEquals(1, dao.deleteById(id))
        assertEquals(0, dao.observeAll().first().size)
    }
}
