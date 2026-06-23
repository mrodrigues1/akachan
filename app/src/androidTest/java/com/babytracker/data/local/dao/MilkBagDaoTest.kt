package com.babytracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MilkBagDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var bagDao: MilkBagDao
    private lateinit var pumpingDao: PumpingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bagDao = db.milkBagDao()
        pumpingDao = db.pumpingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun activeBagsExcludeUsed() = runTest {
        bagDao.insert(MilkBagEntity(collectionDate = 100L, volumeMl = 100, createdAt = 100L))
        bagDao.insert(
            MilkBagEntity(collectionDate = 50L, volumeMl = 150, createdAt = 50L, usedAt = 200L)
        )
        bagDao.getActiveBags().test {
            val active = awaitItem()
            assertEquals(1, active.size)
            assertEquals(100, active[0].volumeMl)
            cancelAndIgnoreRemainingEvents()
        }
        bagDao.getAllBags().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun summaryAggregatesActiveBags() = runTest {
        bagDao.insert(MilkBagEntity(collectionDate = 200L, volumeMl = 100, createdAt = 200L))
        bagDao.insert(MilkBagEntity(collectionDate = 100L, volumeMl = 60, createdAt = 100L))
        bagDao.insert(
            MilkBagEntity(collectionDate = 50L, volumeMl = 999, createdAt = 50L, usedAt = 300L)
        )
        bagDao.getActiveSummary().test {
            val row = awaitItem()
            assertEquals(160, row.totalMl)
            assertEquals(2, row.bagCount)
            assertEquals(100L, row.oldestBagDateMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sumVolumeForIdsSumsRequestedBagsIgnoringUsedStateAndMissingIds() = runTest {
        val a = bagDao.insert(MilkBagEntity(collectionDate = 100L, volumeMl = 60, createdAt = 100L))
        val b = bagDao.insert(
            MilkBagEntity(collectionDate = 200L, volumeMl = 150, createdAt = 200L, usedAt = 300L)
        )
        bagDao.insert(MilkBagEntity(collectionDate = 300L, volumeMl = 999, createdAt = 300L))

        // Sums the requested ids regardless of used state; the third (unreferenced) bag is excluded.
        assertEquals(210, bagDao.sumVolumeForIds(listOf(a, b)))
        // A missing id contributes 0 (matches the caller's previous per-id `?: 0` fallback).
        assertEquals(60, bagDao.sumVolumeForIds(listOf(a, 99_999L)))
    }

    @Test
    fun foreignKeySetNullOnSessionDelete() = runTest {
        val sessionId = pumpingDao.insert(
            PumpingEntity(startTime = 1_000L, breast = "LEFT")
        )
        val bagId = bagDao.insert(
            MilkBagEntity(
                collectionDate = 1_000L,
                volumeMl = 100,
                createdAt = 1_000L,
                sourceSessionId = sessionId,
            )
        )
        val session = pumpingDao.getById(sessionId)!!
        pumpingDao.delete(session)

        bagDao.getAllBags().test {
            val all = awaitItem()
            val bag = all.first { it.id == bagId }
            assertNull(bag.sourceSessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateActiveDetailsOnlyUpdatesEditableFields() = runTest {
        val sessionId = pumpingDao.insert(
            PumpingEntity(startTime = 800L, breast = "LEFT")
        )
        val bagId = bagDao.insert(
            MilkBagEntity(
                collectionDate = 1_000L,
                volumeMl = 100,
                sourceSessionId = sessionId,
                usedAt = null,
                notes = "Old",
                createdAt = 900L,
            )
        )

        val updatedRows = bagDao.updateActiveDetails(
            id = bagId,
            collectionDate = 2_000L,
            volumeMl = 150,
            notes = "New",
        )

        assertEquals(1, updatedRows)
        bagDao.getAllBags().test {
            val bag = awaitItem().single()
            assertEquals(2_000L, bag.collectionDate)
            assertEquals(150, bag.volumeMl)
            assertEquals("New", bag.notes)
            assertEquals(sessionId, bag.sourceSessionId)
            assertNull(bag.usedAt)
            assertEquals(900L, bag.createdAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateActiveDetailsDoesNotUpdateUsedBag() = runTest {
        val bagId = bagDao.insert(
            MilkBagEntity(
                collectionDate = 1_000L,
                volumeMl = 100,
                usedAt = 1_500L,
                notes = "Old",
                createdAt = 900L,
            )
        )

        val updatedRows = bagDao.updateActiveDetails(
            id = bagId,
            collectionDate = 2_000L,
            volumeMl = 150,
            notes = "New",
        )

        assertEquals(0, updatedRows)
        bagDao.getAllBags().test {
            val bag = awaitItem().single()
            assertEquals(1_000L, bag.collectionDate)
            assertEquals(100, bag.volumeMl)
            assertEquals("Old", bag.notes)
            assertEquals(1_500L, bag.usedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
