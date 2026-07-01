package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class LogDiaperChangeUseCaseTest {
    private lateinit var repository: DiaperRepository
    private val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: LogDiaperChangeUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = LogDiaperChangeUseCase(repository, SyncedWrite(sync)) { fixedNow }
    }

    @Test
    fun `inserts with stamped createdAt and trimmed notes`() = runTest {
        val captured = slot<DiaperChange>()
        coEvery { repository.insert(capture(captured)) } returns 1
        useCase(DiaperType.BOTH, Instant.ofEpochMilli(9_000), "   ")
        assertEquals(fixedNow, captured.captured.createdAt)
        assertEquals(null, captured.captured.notes)
        assertEquals(DiaperType.BOTH, captured.captured.type)
        coVerify { sync(SyncToFirestoreUseCase.SyncType.DIAPERS) }
    }

    @Test
    fun `rejects future timestamps`() = runTest {
        val error = runCatching { useCase(DiaperType.WET, Instant.ofEpochMilli(20_000)) }
            .exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
