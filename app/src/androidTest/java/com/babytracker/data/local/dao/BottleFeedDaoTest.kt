package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BottleFeedEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottleFeedDaoTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: BottleFeedDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bottleFeedDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAll_returnsNewestFirst() = runTest {
        dao.insert(feed(timestamp = 1_000))
        dao.insert(feed(timestamp = 5_000))

        val all = dao.getAll().first()

        assertEquals(listOf(5_000L, 1_000L), all.map { it.timestamp })
    }

    @Test
    fun updateDetails_mutatesRow() = runTest {
        val id = dao.insert(feed(timestamp = 1_000, volumeMl = 100))

        val changed = dao.updateDetails(id, 2_000, 200, "FORMULA", null, "edited")

        assertEquals(1, changed)
        val row = dao.getAll().first().first()
        assertEquals(200, row.volumeMl)
        assertEquals("FORMULA", row.type)
        assertEquals("edited", row.notes)
    }

    private fun feed(
        timestamp: Long,
        volumeMl: Int = 90,
    ) = BottleFeedEntity(
        timestamp = timestamp,
        volumeMl = volumeMl,
        type = "BREAST_MILK",
        createdAt = timestamp,
    )
}
