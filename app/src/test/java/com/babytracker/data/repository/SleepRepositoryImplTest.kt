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
import org.junit.jupiter.api.Assertions.assertEquals
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
        sleepType = "NAP"
    )

    @BeforeEach
    fun setUp() {
        dao = mockk()
        repository = SleepRepositoryImpl(dao)
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
        coEvery { dao.getRecentRecords(capture(slot)) } returns emptyList()

        repository.getRecentRecords(5)

        assertEquals(5, slot.captured)
    }

    @Test
    fun getRecentRecordsMapsResultsToDomain() = runTest {
        coEvery { dao.getRecentRecords(any()) } returns listOf(entity)

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
    fun updateRecordConvertsToEntityBeforeUpdate() = runTest {
        val record = SleepRecord(
            id = 1L,
            startTime = Instant.ofEpochMilli(startEpoch),
            endTime = Instant.ofEpochMilli(endEpoch),
            sleepType = SleepType.NAP
        )
        val slot = slot<SleepEntity>()
        coJustRun { dao.updateRecord(capture(slot)) }

        repository.updateRecord(record)

        assertEquals(endEpoch, slot.captured.endTime)
        assertEquals(1L, slot.captured.id)
    }
}
