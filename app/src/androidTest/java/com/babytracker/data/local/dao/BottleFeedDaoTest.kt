package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.data.local.entity.MilkBagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottleFeedDaoTest {

    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: BottleFeedDao
    private lateinit var milkBagDao: MilkBagDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bottleFeedDao()
        milkBagDao = db.milkBagDao()
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

    @Test
    fun updateDetailsWithInventory_swapsLinkedBagsInOneTransaction() = runTest {
        val oldBagId = milkBagDao.insert(bag(usedAt = 1_500))
        val newBagId = milkBagDao.insert(bag(collectionDate = 2_000, usedAt = null))
        val feedId = dao.insert(feed(timestamp = 1_000, linkedMilkBagId = oldBagId))

        val updated = dao.updateDetailsWithInventory(
            id = feedId,
            timestamp = 3_000,
            volumeMl = 120,
            type = "BREAST_MILK",
            linkedMilkBagId = newBagId,
            notes = null,
            usedAt = 3_500,
        )

        assertEquals(true, updated)
        assertEquals(newBagId, dao.getById(feedId)?.linkedMilkBagId)
        assertEquals(null, milkBagDao.getById(oldBagId)?.usedAt)
        assertEquals(3_500L, milkBagDao.getById(newBagId)?.usedAt)
    }

    @Test
    fun updateDetailsWithInventory_rejectsAlreadyUsedNewBag() = runTest {
        val usedBagId = milkBagDao.insert(bag(usedAt = 1_500))
        val feedId = dao.insert(feed(timestamp = 1_000, linkedMilkBagId = null))

        try {
            dao.updateDetailsWithInventory(
                id = feedId,
                timestamp = 3_000,
                volumeMl = 120,
                type = "BREAST_MILK",
                linkedMilkBagId = usedBagId,
                notes = null,
                usedAt = 3_500,
            )
            fail("Expected used bag link to be rejected")
        } catch (_: IllegalStateException) {
            assertEquals(null, dao.getById(feedId)?.linkedMilkBagId)
            assertEquals(1_500L, milkBagDao.getById(usedBagId)?.usedAt)
        }
    }

    @Test
    fun updateDetailsWithInventory_doesNotRestoreBagStillReferencedByAnotherFeed() = runTest {
        val bagId = milkBagDao.insert(bag(usedAt = 1_500))
        val firstFeedId = dao.insert(feed(timestamp = 1_000, linkedMilkBagId = bagId))
        dao.insert(feed(timestamp = 2_000, linkedMilkBagId = bagId))

        val updated = dao.updateDetailsWithInventory(
            id = firstFeedId,
            timestamp = 3_000,
            volumeMl = 120,
            type = "FORMULA",
            linkedMilkBagId = null,
            notes = null,
            usedAt = 3_500,
        )

        assertEquals(true, updated)
        assertEquals(null, dao.getById(firstFeedId)?.linkedMilkBagId)
        assertEquals(1_500L, milkBagDao.getById(bagId)?.usedAt)
    }

    @Test
    fun deleteWithInventoryRestore_deletesFeedAndRestoresLinkedBag() = runTest {
        val bagId = milkBagDao.insert(bag(usedAt = 1_500))
        val feedId = dao.insert(feed(timestamp = 1_000, linkedMilkBagId = bagId))

        val deleted = dao.deleteWithInventoryRestore(feedId)

        assertEquals(true, deleted)
        assertEquals(null, dao.getById(feedId))
        assertEquals(null, milkBagDao.getById(bagId)?.usedAt)
    }

    @Test
    fun deleteWithInventoryRestore_doesNotRestoreBagStillReferencedByAnotherFeed() = runTest {
        val bagId = milkBagDao.insert(bag(usedAt = 1_500))
        val firstFeedId = dao.insert(feed(timestamp = 1_000, linkedMilkBagId = bagId))
        dao.insert(feed(timestamp = 2_000, linkedMilkBagId = bagId))

        val deleted = dao.deleteWithInventoryRestore(firstFeedId)

        assertEquals(true, deleted)
        assertEquals(null, dao.getById(firstFeedId))
        assertEquals(1_500L, milkBagDao.getById(bagId)?.usedAt)
    }

    private fun feed(
        timestamp: Long,
        volumeMl: Int = 90,
        linkedMilkBagId: Long? = null,
    ) = BottleFeedEntity(
        timestamp = timestamp,
        volumeMl = volumeMl,
        type = "BREAST_MILK",
        linkedMilkBagId = linkedMilkBagId,
        createdAt = timestamp,
    )

    private fun bag(
        collectionDate: Long = 1_000,
        usedAt: Long?,
    ) = MilkBagEntity(
        collectionDate = collectionDate,
        volumeMl = 120,
        usedAt = usedAt,
        createdAt = collectionDate,
    )
}
