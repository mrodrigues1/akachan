package com.babytracker.data.local.dao

import com.babytracker.data.local.entity.SleepEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// spyk runs the real @Transaction method bodies against stubbed query methods; the
// transactional wrapping itself is Room-generated and covered by androidTest.
class SleepDaoTest {

    private val dao = spyk<SleepDao>()

    private val entity = SleepEntity(
        id = 1L,
        startTime = 1_700_000_000_000L,
        endTime = 1_700_000_060_000L,
        sleepType = "NAP",
        clientId = "edit-cid",
    )

    @Test
    fun updateRecordPreservingIdentityKeepsClientIdAndStartedByFromExistingRow() = runTest {
        // The edit carries its own identity; the persisted row must keep the existing PARTNER
        // attribution and client_id, otherwise the unique index and ownership break.
        val existing = entity.copy(clientId = "partner-cid", startedBy = "PARTNER")
        val slot = slot<SleepEntity>()
        coEvery { dao.getById(1L) } returns existing
        coJustRun { dao.updateRecord(capture(slot)) }

        dao.updateRecordPreservingIdentity(entity)

        assertEquals("partner-cid", slot.captured.clientId)
        assertEquals("PARTNER", slot.captured.startedBy)
        assertEquals(entity.endTime, slot.captured.endTime)
    }

    @Test
    fun updateRecordPreservingIdentityWritesEntityAsIsWhenRowMissing() = runTest {
        val slot = slot<SleepEntity>()
        coEvery { dao.getById(1L) } returns null
        coJustRun { dao.updateRecord(capture(slot)) }

        dao.updateRecordPreservingIdentity(entity)

        assertEquals("edit-cid", slot.captured.clientId)
    }
}
