package com.babytracker.sharing.data.repository

import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SharingRepositoryImplTest {

    private lateinit var service: FirestoreSharingService
    private lateinit var repository: SharingRepositoryImpl

    private val code = ShareCode("ABCD1234")

    @BeforeEach
    fun setUp() {
        service = mockk()
        repository = SharingRepositoryImpl(service)
    }

    @Test
    fun signInAnonymouslyDelegatesToServiceReturnsUid() = runTest {
        coEvery { service.signInAnonymously() } returns "uid-abc"

        val result = repository.signInAnonymously()

        assertEquals("uid-abc", result)
    }

    @Test
    fun createShareDocumentPassesCodeValueAndOwnerUid() = runTest {
        val codeSlot = slot<String>()
        val uidSlot = slot<String>()
        coJustRun { service.createShareDocument(capture(codeSlot), capture(uidSlot)) }

        repository.createShareDocument(code, "owner-uid")

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals("owner-uid", uidSlot.captured)
    }

    @Test
    fun isShareCodeValidValidReturnsTrue() = runTest {
        coEvery { service.isShareCodeValid("ABCD1234") } returns true

        val result = repository.isShareCodeValid(code)

        assertTrue(result)
    }

    @Test
    fun isShareCodeValidInvalidReturnsFalse() = runTest {
        coEvery { service.isShareCodeValid("ABCD1234") } returns false

        val result = repository.isShareCodeValid(code)

        assertFalse(result)
    }

    @Test
    fun syncFullSnapshotPassesCodeValueAndSnapshot() = runTest {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.now(),
            baby = BabySnapshot("Luna", 0L, emptyList()),
            sessions = emptyList(),
            sleepRecords = emptyList(),
        )
        val codeSlot = slot<String>()
        val snapshotSlot = slot<ShareSnapshot>()
        coJustRun { service.syncFullSnapshot(capture(codeSlot), capture(snapshotSlot)) }

        repository.syncFullSnapshot(code, snapshot)

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals(snapshot, snapshotSlot.captured)
    }

    @Test
    fun syncSessionsPassesCodeValueAndList() = runTest {
        val sessions = listOf(
            SessionSnapshot(
                id = 1L,
                startTime = 1000L,
                endTime = null,
                startingSide = "LEFT",
                switchTime = null,
                pausedDurationMs = 0L,
                notes = null,
            ),
        )
        val codeSlot = slot<String>()
        val sessionsSlot = slot<List<SessionSnapshot>>()
        coJustRun { service.syncSessions(capture(codeSlot), capture(sessionsSlot)) }

        repository.syncSessions(code, sessions)

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals(sessions, sessionsSlot.captured)
    }

    @Test
    fun syncSleepRecordsPassesCodeValueAndList() = runTest {
        val sleepRecords = listOf(
            SleepSnapshot(
                id = 1L,
                startTime = 1000L,
                endTime = 2000L,
                sleepType = "NAP",
                notes = null,
            ),
        )
        val codeSlot = slot<String>()
        val sleepSlot = slot<List<SleepSnapshot>>()
        coJustRun { service.syncSleepRecords(capture(codeSlot), capture(sleepSlot)) }

        repository.syncSleepRecords(code, sleepRecords)

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals(sleepRecords, sleepSlot.captured)
    }

    @Test
    fun syncBabyPassesCodeValueAndSnapshot() = runTest {
        val baby = BabySnapshot("Luna", 0L, listOf("CMPA"))
        val codeSlot = slot<String>()
        val babySlot = slot<BabySnapshot>()
        coJustRun { service.syncBaby(capture(codeSlot), capture(babySlot)) }

        repository.syncBaby(code, baby)

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals(baby, babySlot.captured)
    }

    @Test
    fun registerPartnerPassesCodeValueAndUid() = runTest {
        val codeSlot = slot<String>()
        val uidSlot = slot<String>()
        coJustRun { service.registerPartner(capture(codeSlot), capture(uidSlot)) }

        repository.registerPartner(code, "partner-uid")

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals("partner-uid", uidSlot.captured)
    }

    @Test
    fun fetchSnapshotPassesCodeValueReturnsSnapshot() = runTest {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.now(),
            baby = BabySnapshot("Luna", 0L, emptyList()),
            sessions = emptyList(),
            sleepRecords = emptyList(),
        )
        coEvery { service.fetchSnapshot("ABCD1234") } returns snapshot

        val result = repository.fetchSnapshot(code)

        assertEquals(snapshot, result)
    }

    @Test
    fun isPartnerConnectedPassesCodeValueAndUidReturnsTrue() = runTest {
        coEvery { service.isPartnerConnected("ABCD1234", "uid-xyz") } returns true

        val result = repository.isPartnerConnected(code, "uid-xyz")

        assertTrue(result)
    }

    @Test
    fun getPartnersPassesCodeValueReturnsList() = runTest {
        val partners = listOf(PartnerInfo(uid = "uid-1", connectedAt = Instant.now()))
        coEvery { service.getPartners("ABCD1234") } returns partners

        val result = repository.getPartners(code)

        assertEquals(partners, result)
    }

    @Test
    fun revokePartnerPassesCodeValueAndUid() = runTest {
        val codeSlot = slot<String>()
        val uidSlot = slot<String>()
        coJustRun { service.revokePartner(capture(codeSlot), capture(uidSlot)) }

        repository.revokePartner(code, "uid-to-revoke")

        assertEquals("ABCD1234", codeSlot.captured)
        assertEquals("uid-to-revoke", uidSlot.captured)
    }

    @Test
    fun deleteShareDocumentPassesCodeValue() = runTest {
        val codeSlot = slot<String>()
        coJustRun { service.deleteShareDocument(capture(codeSlot)) }

        repository.deleteShareDocument(code)

        assertEquals("ABCD1234", codeSlot.captured)
    }
}
