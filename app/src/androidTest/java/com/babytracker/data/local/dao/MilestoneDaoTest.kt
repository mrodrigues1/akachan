package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.MilestoneAchievementEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun achievement(milestone: String, day: Long) =
        MilestoneAchievementEntity(milestone = milestone, achievedOnEpochDay = day)

    @Test
    fun upsert_replacesExistingRowForSameMilestone() = runTest {
        dao.upsert(achievement("WALKING_ALONE", day = 100))
        dao.upsert(achievement("WALKING_ALONE", day = 200))

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals(200L, all.first().achievedOnEpochDay)
    }

    @Test
    fun upsert_keepsDistinctMilestonesSeparate() = runTest {
        dao.upsert(achievement("WALKING_ALONE", day = 100))
        dao.upsert(achievement("STANDING_ALONE", day = 150))

        assertEquals(2, dao.getAll().first().size)
    }

    @Test
    fun deleteByMilestone_removesOnlyThatMilestone() = runTest {
        dao.upsert(achievement("WALKING_ALONE", day = 100))
        dao.upsert(achievement("STANDING_ALONE", day = 150))

        dao.deleteByMilestone("WALKING_ALONE")

        val remaining = dao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals("STANDING_ALONE", remaining.first().milestone)
    }
}
