package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.DoctorVisitDao
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import com.babytracker.domain.model.DoctorVisit
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

class DoctorVisitRepositoryImplTest {
    private lateinit var dao: DoctorVisitDao
    private lateinit var repository: DoctorVisitRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = DoctorVisitRepositoryImpl(dao)
    }

    @Test
    fun `observeAllVisits maps entities to domain`() = runTest {
        every { dao.observeAllVisits() } returns flowOf(
            listOf(DoctorVisitEntity(id = 1, date = 10, providerName = "Dr A", createdAt = 5)),
        )
        repository.observeAllVisits().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Dr A", list.first().providerName)
            awaitComplete()
        }
    }

    @Test
    fun `observeInboxQuestions maps entities to domain`() = runTest {
        every { dao.observeInboxQuestions() } returns flowOf(
            listOf(VisitQuestionEntity(id = 1, text = "Q", visitId = null, createdAt = 5)),
        )
        repository.observeInboxQuestions().test {
            assertEquals("Q", awaitItem().first().text)
            awaitComplete()
        }
    }

    @Test
    fun `insertVisit converts to entity`() = runTest {
        val captured = slot<DoctorVisitEntity>()
        coEvery { dao.insertVisit(capture(captured)) } returns 42
        val id = repository.insertVisit(
            DoctorVisit(date = Instant.ofEpochMilli(10), providerName = "Dr B", createdAt = Instant.ofEpochMilli(5)),
        )
        assertEquals(42, id)
        assertEquals(10L, captured.captured.date)
        assertEquals("Dr B", captured.captured.providerName)
    }

    @Test
    fun `attachQuestions skips empty list`() = runTest {
        repository.attachQuestions(emptyList(), 9)
        coVerify(exactly = 0) { dao.attachQuestions(any(), any()) }
        repository.attachQuestions(listOf(1, 2), 9)
        coVerify { dao.attachQuestions(listOf(1, 2), 9) }
    }
}
