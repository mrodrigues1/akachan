package com.babytracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PumpingDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: PumpingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pumpingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertAndGetActive() = runTest {
        val id = dao.insert(
            PumpingEntity(
                startTime = 1_000L,
                breast = "LEFT",
            )
        )
        dao.getActiveSession().test {
            val active = awaitItem()
            assertNotNull(active)
            assertEquals(id, active!!.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stoppedSessionRemovedFromActive() = runTest {
        val id = dao.insert(PumpingEntity(startTime = 1_000L, breast = "RIGHT"))
        dao.update(
            PumpingEntity(
                id = id,
                startTime = 1_000L,
                endTime = 2_000L,
                breast = "RIGHT",
                volumeMl = 80,
            )
        )
        dao.getActiveSession().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRemovesRow() = runTest {
        val id = dao.insert(PumpingEntity(startTime = 1_000L, breast = "BOTH"))
        val entity = dao.getById(id)!!
        dao.delete(entity)
        assertNull(dao.getById(id))
    }
}
