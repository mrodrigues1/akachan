package com.babytracker.sharing.usecase

import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncedWriteTest {
    private val syncToFirestore = mockk<SyncToFirestoreUseCase>(relaxed = true)
    private val syncedWrite = SyncedWrite(syncToFirestore)

    @Test
    fun `runs the write, then syncs, and returns the write result`() = runTest {
        val writes = mutableListOf<String>()
        coEvery { syncToFirestore(SyncType.DIAPERS) } answers { writes.add("sync") }

        val result = syncedWrite(SyncType.DIAPERS) {
            writes.add("write")
            42L
        }

        assertEquals(42L, result)
        assertEquals(listOf("write", "sync"), writes)
    }

    @Test
    fun `a sync failure never fails the write`() = runTest {
        coEvery { syncToFirestore(SyncType.SESSIONS) } throws RuntimeException("offline")

        val result = syncedWrite(SyncType.SESSIONS) { 7L }

        assertEquals(7L, result)
        coVerify(exactly = 1) { syncToFirestore(SyncType.SESSIONS) }
    }

    @Test
    fun `a failed write propagates and skips the sync`() = runTest {
        val error = runCatching {
            syncedWrite(SyncType.INVENTORY) { error("db full") }
        }.exceptionOrNull()

        assertEquals(IllegalStateException::class.java, error?.javaClass)
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }

    @Test
    fun `sync reports success and swallows failure`() = runTest {
        assertTrue(syncedWrite.sync(SyncType.INVENTORY))

        coEvery { syncToFirestore(SyncType.INVENTORY) } throws RuntimeException("offline")
        assertFalse(syncedWrite.sync(SyncType.INVENTORY))
    }

    @Test
    fun `sync defaults to a full snapshot push`() = runTest {
        syncedWrite.sync()
        coVerifyOrder { syncToFirestore(SyncType.FULL) }
    }
}
