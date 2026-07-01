package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class EditDiaperChangeUseCaseTest {
    private lateinit var repository: DiaperRepository
    private val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: EditDiaperChangeUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = EditDiaperChangeUseCase(repository, SyncedWrite(sync)) { fixedNow }
    }

    @Test
    fun `updates with blank notes normalized to null`() = runTest {
        val captured = slot<DiaperChange>()
        useCase(
            DiaperChange(
                id = 4,
                timestamp = Instant.ofEpochMilli(9_000),
                type = DiaperType.WET,
                notes = "  ",
                createdAt = Instant.ofEpochMilli(8_000),
            ),
        )
        coVerify { repository.update(capture(captured)) }
        assertEquals(null, captured.captured.notes)
        coVerify { sync(SyncToFirestoreUseCase.SyncType.DIAPERS) }
    }

    @Test
    fun `rejects future timestamps`() = runTest {
        val error = runCatching {
            useCase(
                DiaperChange(
                    id = 4,
                    timestamp = Instant.ofEpochMilli(20_000),
                    type = DiaperType.WET,
                    createdAt = Instant.ofEpochMilli(8_000),
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
