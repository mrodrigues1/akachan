package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.MilestoneDao
import com.babytracker.data.local.entity.MilestoneEntity
import com.babytracker.domain.model.Milestone
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class MilestoneRepositoryImplTest {

    private lateinit var dao: MilestoneDao
    private lateinit var repository: MilestoneRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        repository = MilestoneRepositoryImpl(dao)
    }

    @Test
    fun `addMilestone inserts the entity with a fresh id`() = runTest {
        coEvery { dao.insert(any()) } returns 7L

        val id = repository.addMilestone(
            Milestone(
                id = 99, // a stale caller id must be ignored
                title = "First smile",
                date = LocalDate.of(2026, 6, 1),
                time = LocalTime.of(8, 30),
                photoUri = "content://photo",
                note = "so cute",
            ),
        )

        assertEquals(7L, id)
        val slot = slot<MilestoneEntity>()
        coVerify { dao.insert(capture(slot)) }
        assertEquals(0L, slot.captured.id)
        assertEquals("First smile", slot.captured.title)
        assertEquals(LocalDate.of(2026, 6, 1).toEpochDay(), slot.captured.dateEpochDay)
        assertEquals(8 * 60 + 30, slot.captured.timeMinuteOfDay)
        assertEquals("content://photo", slot.captured.photoUri)
    }

    @Test
    fun `getMilestones maps entities to domain`() = runTest {
        every { dao.getAll() } returns flowOf(
            listOf(
                MilestoneEntity(id = 1, title = "Trip", dateEpochDay = 100, timeMinuteOfDay = null),
                MilestoneEntity(id = 2, title = "Bath", dateEpochDay = 200, timeMinuteOfDay = 90),
            ),
        )

        repository.getMilestones().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals("Trip", list[0].title)
            assertNull(list[0].time)
            assertEquals(LocalTime.of(1, 30), list[1].time)
            awaitComplete()
        }
    }

    @Test
    fun `getMilestone maps a single entity`() = runTest {
        every { dao.getById(5) } returns flowOf(
            MilestoneEntity(id = 5, title = "Crawl", dateEpochDay = 300, timeMinuteOfDay = null),
        )

        repository.getMilestone(5).test {
            assertEquals("Crawl", awaitItem()?.title)
            awaitComplete()
        }
    }

    @Test
    fun `updateMilestone delegates to the dao`() = runTest {
        coEvery { dao.update(any()) } just Runs

        repository.updateMilestone(
            Milestone(id = 3, title = "Updated", date = LocalDate.of(2026, 1, 1)),
        )

        val slot = slot<MilestoneEntity>()
        coVerify { dao.update(capture(slot)) }
        assertEquals(3L, slot.captured.id)
        assertEquals("Updated", slot.captured.title)
    }

    @Test
    fun `deleteMilestone delegates to the dao by id`() = runTest {
        coEvery { dao.deleteById(4) } just Runs

        repository.deleteMilestone(4)

        coVerify { dao.deleteById(4) }
    }
}
