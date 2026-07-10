package com.babytracker.data.repository

import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import android.database.sqlite.SQLiteConstraintException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SleepRepositoryImplTest {

    private lateinit var dao: SleepDao
    private lateinit var repository: SleepRepositoryImpl

    private val startEpoch = 1_700_000_000_000L
    private val endEpoch = 1_700_000_060_000L

    private val entity = SleepEntity(
        id = 1L,
        startTime = startEpoch,
        endTime = endEpoch,
        sleepType = "NAP",
        clientId = "cid-1",
    )

    @BeforeEach
    fun setUp() {
        dao = mockk()
        repository = SleepRepositoryImpl(dao, mockk())
    }

    @Test
    fun getAllRecordsMapsEntitiesToDomain() = runTest {
        every { dao.getAllRecords() } returns flowOf(listOf(entity))

        val records = repository.getAllRecords().first()

        assertEquals(1, records.size)
        assertEquals(1L, records[0].id)
        assertEquals(SleepType.NAP, records[0].sleepType)
        assertEquals(Instant.ofEpochMilli(startEpoch), records[0].startTime)
        assertEquals(Instant.ofEpochMilli(endEpoch), records[0].endTime)
    }

    @Test
    fun getAllRecordsEmptyListEmitsEmpty() = runTest {
        every { dao.getAllRecords() } returns flowOf(emptyList())

        val records = repository.getAllRecords().first()

        assertEquals(0, records.size)
    }

    @Test
    fun getRecordsSinceFlowPassesEpochMsAndMapsToDomain() = runTest {
        val slot = slot<Long>()
        every { dao.getRecordsSinceFlow(capture(slot)) } returns flowOf(listOf(entity))

        val records = repository.getRecordsSinceFlow(Instant.ofEpochMilli(1_000_000L)).first()

        assertEquals(1_000_000L, slot.captured)
        assertEquals(1L, records[0].id)
        assertEquals(Instant.ofEpochMilli(startEpoch), records[0].startTime)
    }

    @Test
    fun getCompletedRecordsSincePassesEpochMsToDao() = runTest {
        val since = Instant.ofEpochMilli(1_000_000L)
        val slot = slot<Long>()
        coEvery { dao.getCompletedRecordsSince(capture(slot)) } returns emptyList()

        repository.getCompletedRecordsSince(since)

        assertEquals(1_000_000L, slot.captured)
    }

    @Test
    fun getCompletedRecordsSinceMapsResultsToDomain() = runTest {
        coEvery { dao.getCompletedRecordsSince(any()) } returns listOf(entity)

        val results = repository.getCompletedRecordsSince(Instant.ofEpochMilli(0L))

        assertEquals(1, results.size)
        assertEquals(SleepType.NAP, results[0].sleepType)
    }

    @Test
    fun getCompletedRecordsSinceEmptyResultReturnsEmpty() = runTest {
        coEvery { dao.getCompletedRecordsSince(any()) } returns emptyList()

        val results = repository.getCompletedRecordsSince(Instant.now())

        assertEquals(0, results.size)
    }

    @Test
    fun getRecentRecordsPassesLimitToDao() = runTest {
        val slot = slot<Int>()
        every { dao.getRecentRecordsFlow(capture(slot)) } returns flowOf(emptyList())

        repository.getRecentRecords(5)

        assertEquals(5, slot.captured)
    }

    @Test
    fun getRecentRecordsMapsResultsToDomain() = runTest {
        every { dao.getRecentRecordsFlow(any()) } returns flowOf(listOf(entity))

        val results = repository.getRecentRecords(1)

        assertEquals(1, results.size)
        assertEquals(1L, results[0].id)
    }

    @Test
    fun insertRecordConvertsToEntityBeforeInsert() = runTest {
        val record = SleepRecord(
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            sleepType = SleepType.NIGHT_SLEEP
        )
        val slot = slot<SleepEntity>()
        coEvery { dao.insertRecord(capture(slot)) } returns 99L

        repository.insertRecord(record)

        assertEquals(startEpoch, slot.captured.startTime)
        assertEquals(endEpoch, slot.captured.endTime)
        assertEquals("NIGHT_SLEEP", slot.captured.sleepType)
    }

    @Test
    fun insertRecordReturnsIdFromDao() = runTest {
        val record = SleepRecord(startTime = Instant.now(), sleepType = SleepType.NAP)
        coEvery { dao.insertRecord(any()) } returns 77L

        val id = repository.insertRecord(record)

        assertEquals(77L, id)
    }

    @Test
    fun insertRecordMintsClientIdWhenBlank() = runTest {
        // A blank clientId only reaches the repository from a legacy row; the mint keeps it unique.
        val record = SleepRecord(startTime = Instant.now(), sleepType = SleepType.NAP, clientId = "")
        val slot = slot<SleepEntity>()
        coEvery { dao.insertRecord(capture(slot)) } returns 1L

        repository.insertRecord(record)

        assertEquals(true, slot.captured.clientId.isNotBlank())
    }

    @Test
    fun insertRecordDefaultClientIdIsAlreadyMinted() = runTest {
        // The domain default now mints its own UUID, so nothing blank ever reaches the mint path.
        val record = SleepRecord(startTime = Instant.now(), sleepType = SleepType.NAP)
        val slot = slot<SleepEntity>()
        coEvery { dao.insertRecord(capture(slot)) } returns 1L

        repository.insertRecord(record)

        assertEquals(record.clientId, slot.captured.clientId)
    }

    @Test
    fun insertRecordKeepsProvidedClientId() = runTest {
        val record = SleepRecord(
            startTime = Instant.now(),
            sleepType = SleepType.NAP,
            clientId = "explicit-cid",
        )
        val slot = slot<SleepEntity>()
        coEvery { dao.insertRecord(capture(slot)) } returns 1L

        repository.insertRecord(record)

        assertEquals("explicit-cid", slot.captured.clientId)
    }

    @Test
    fun updateRecordConvertsToEntityBeforeUpdate() = runTest {
        val record = SleepRecord(
            id = 1L,
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            sleepType = SleepType.NAP
        )
        val slot = slot<SleepEntity>()
        coJustRun { dao.updateRecordPreservingIdentity(capture(slot)) }

        repository.updateRecord(record)

        assertEquals(endEpoch, slot.captured.endTime)
        assertEquals(1L, slot.captured.id)
    }

    @Test
    fun updateRecordMintsClientIdBeforeDelegating() = runTest {
        val edit = SleepRecord(
            id = 1L,
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            sleepType = SleepType.NAP,
            clientId = "",
        )
        val slot = slot<SleepEntity>()
        coJustRun { dao.updateRecordPreservingIdentity(capture(slot)) }

        repository.updateRecord(edit)

        assertEquals(true, slot.captured.clientId.isNotBlank())
    }

    @Test
    fun deleteRecordPassesIdToDao() = runTest {
        val slot = slot<Long>()
        coJustRun { dao.deleteRecord(capture(slot)) }

        repository.deleteRecord(42L)

        assertEquals(42L, slot.captured)
    }

    @Test
    fun insertRecordActiveConstraintViolationReturnsExistingId() = runTest {
        val activeRecord = SleepRecord(
            startTime = Instant.ofEpochMilli(startEpoch),
            sleepType = SleepType.NAP
        )
        val existingEntity = entity.copy(endTime = null, id = 55L)
        coEvery { dao.insertRecord(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")
        coEvery { dao.getActiveRecordOnce() } returns existingEntity

        val id = repository.insertRecord(activeRecord)

        assertEquals(55L, id)
    }

    @Test
    fun insertRecordActiveConstraintViolationNoActiveRowRethrows() = runTest {
        val activeRecord = SleepRecord(
            startTime = Instant.ofEpochMilli(startEpoch),
            sleepType = SleepType.NAP
        )
        coEvery { dao.insertRecord(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")
        coEvery { dao.getActiveRecordOnce() } returns null

        var thrown: SQLiteConstraintException? = null
        try {
            repository.insertRecord(activeRecord)
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun insertRecordCompletedConstraintViolationAlwaysRethrows() = runTest {
        val completedRecord = SleepRecord(
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            sleepType = SleepType.NAP
        )
        coEvery { dao.insertRecord(any()) } throws SQLiteConstraintException("UNIQUE constraint failed")

        var thrown: SQLiteConstraintException? = null
        try {
            repository.insertRecord(completedRecord)
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun startRecordIfNoneConvertsToEntityAndReturnsId() = runTest {
        val record = SleepRecord(
            startTime = Instant.ofEpochMilli(startEpoch),
            sleepType = SleepType.NAP
        )
        val slot = slot<SleepEntity>()
        coEvery { dao.startRecordIfNone(capture(slot)) } returns 12L

        val result = repository.startRecordIfNone(record)

        assertEquals(12L, result)
        assertEquals("NAP", slot.captured.sleepType)
        assertEquals(startEpoch, slot.captured.startTime)
    }

    @Test
    fun startRecordIfNoneReturnsNullWhenDaoReturnsNull() = runTest {
        val record = SleepRecord(
            startTime = Instant.ofEpochMilli(startEpoch),
            sleepType = SleepType.NIGHT_SLEEP
        )
        coEvery { dao.startRecordIfNone(any()) } returns null

        val result = repository.startRecordIfNone(record)

        assertNull(result)
    }

    @Test
    fun stopActiveRecordPassesEpochMillisToDao() = runTest {
        val endInstant = Instant.ofEpochMilli(endEpoch)
        val slot = slot<Long>()
        coEvery { dao.stopActiveRecord(capture(slot)) } returns true

        repository.stopActiveRecord(endInstant)

        assertEquals(endEpoch, slot.captured)
    }

    @Test
    fun stopActiveRecordReturnsTrueWhenDaoReturnsTrue() = runTest {
        coEvery { dao.stopActiveRecord(any()) } returns true

        val result = repository.stopActiveRecord(Instant.ofEpochMilli(endEpoch))

        assertEquals(true, result)
    }

    @Test
    fun stopActiveRecordReturnsFalseWhenDaoReturnsFalse() = runTest {
        coEvery { dao.stopActiveRecord(any()) } returns false

        val result = repository.stopActiveRecord(Instant.ofEpochMilli(endEpoch))

        assertEquals(false, result)
    }
}
