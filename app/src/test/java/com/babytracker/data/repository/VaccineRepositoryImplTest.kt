package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.VaccineDao
import com.babytracker.data.local.entity.VaccineEntity
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VaccineRepositoryImplTest {
    private lateinit var dao: VaccineDao
    private lateinit var repository: VaccineRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = VaccineRepositoryImpl(dao)
    }

    @Test
    fun `observeAll maps entities to domain`() = runTest {
        every { dao.observeAll() } returns flowOf(
            listOf(VaccineEntity(id = 1, name = "MMR", status = "ADMINISTERED", administeredDate = 10, createdAt = 5)),
        )
        repository.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(VaccineStatus.ADMINISTERED, list.first().status)
            awaitComplete()
        }
    }

    @Test
    fun `insert converts to entity`() = runTest {
        val captured = slot<VaccineEntity>()
        coEvery { dao.insert(capture(captured)) } returns 42
        val id = repository.insert(
            VaccineRecord(
                name = "BCG",
                status = VaccineStatus.SCHEDULED,
                scheduledDate = Instant.ofEpochMilli(10),
                createdAt = Instant.ofEpochMilli(5),
            ),
        )
        assertEquals(42, id)
        assertEquals("SCHEDULED", captured.captured.status)
        assertEquals(10L, captured.captured.scheduledDate)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        repository.deleteById(9)
        coVerify { dao.deleteById(9) }
    }

    @Test
    fun `getToScheduleFutureAfter maps dao rows to domain`() = runTest {
        val entity = VaccineEntity(
            id = 7,
            name = "MMR",
            status = "TO_SCHEDULE",
            scheduledDate = 1_900_000_000_000,
            createdAt = 1_700_000_000_000,
        )
        coEvery { dao.getToScheduleFutureAfter(any()) } returns listOf(entity)

        val result = repository.getToScheduleFutureAfter(1_800_000_000_000)

        assertEquals(1, result.size)
        assertEquals(VaccineStatus.TO_SCHEDULE, result.first().status)
        coVerify { dao.getToScheduleFutureAfter(1_800_000_000_000) }
    }
}
