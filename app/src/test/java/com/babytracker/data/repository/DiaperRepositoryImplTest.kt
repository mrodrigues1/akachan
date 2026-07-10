package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.DiaperDao
import com.babytracker.data.local.entity.DiaperEntity
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
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

class DiaperRepositoryImplTest {
    private lateinit var dao: DiaperDao
    private lateinit var repository: DiaperRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = DiaperRepositoryImpl(dao)
    }

    @Test
    fun `observeRecent maps entities to domain`() = runTest {
        every { dao.observeRecent(1) } returns flowOf(
            listOf(DiaperEntity(id = 1, timestamp = 10, type = "WET", notes = null, createdAt = 5)),
        )
        repository.observeRecent(1).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(DiaperType.WET, list.first().type)
            awaitComplete()
        }
    }

    @Test
    fun `insert converts to entity`() = runTest {
        val captured = slot<DiaperEntity>()
        coEvery { dao.insert(capture(captured)) } returns 42
        val id = repository.insert(
            DiaperChange(
                timestamp = Instant.ofEpochMilli(10),
                type = DiaperType.DIRTY,
                createdAt = Instant.ofEpochMilli(5),
            ),
        )
        assertEquals(42, id)
        assertEquals("DIRTY", captured.captured.type)
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        repository.deleteById(9)
        coVerify { dao.deleteById(9) }
    }
}
