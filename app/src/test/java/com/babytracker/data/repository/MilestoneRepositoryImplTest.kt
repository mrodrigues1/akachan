package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.MilestoneDao
import com.babytracker.data.local.entity.MilestoneAchievementEntity
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestoneRepositoryImplTest {

    private lateinit var dao: MilestoneDao
    private lateinit var repository: MilestoneRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        repository = MilestoneRepositoryImpl(dao)
    }

    @Test
    fun `logAchievement upserts the entity`() = runTest {
        coEvery { dao.upsert(any()) } returns 1L

        repository.logAchievement(
            MilestoneAchievement(
                id = 99, // a stale caller id must be ignored
                milestone = Milestone.WALKING_ALONE,
                achievedOn = LocalDate.of(2026, 6, 1),
                photoUri = "content://photo",
                notes = "first steps",
            ),
        )

        val slot = slot<MilestoneAchievementEntity>()
        coVerify { dao.upsert(capture(slot)) }
        assertEquals(0L, slot.captured.id) // forced fresh id; milestone index is the only conflict key
        assertEquals("WALKING_ALONE", slot.captured.milestone)
        assertEquals(LocalDate.of(2026, 6, 1).toEpochDay(), slot.captured.achievedOnEpochDay)
        assertEquals("content://photo", slot.captured.photoUri)
    }

    @Test
    fun `getAchievements maps entities and drops unknown milestone names`() = runTest {
        every { dao.getAll() } returns flowOf(
            listOf(
                MilestoneAchievementEntity(id = 1, milestone = "WALKING_ALONE", achievedOnEpochDay = 100),
                MilestoneAchievementEntity(id = 2, milestone = "RETIRED_MILESTONE", achievedOnEpochDay = 200),
            ),
        )

        repository.getAchievements().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(Milestone.WALKING_ALONE, list.first().milestone)
            awaitComplete()
        }
    }

    @Test
    fun `deleteAchievement delegates to dao by milestone name`() = runTest {
        coEvery { dao.deleteByMilestone("STANDING_ALONE") } just Runs

        repository.deleteAchievement(Milestone.STANDING_ALONE)

        coVerify { dao.deleteByMilestone("STANDING_ALONE") }
    }
}
