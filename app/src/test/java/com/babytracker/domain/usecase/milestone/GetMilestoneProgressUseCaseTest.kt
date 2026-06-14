package com.babytracker.domain.usecase.milestone

import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import com.babytracker.domain.repository.MilestoneRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GetMilestoneProgressUseCaseTest {

    @Test
    fun `joins the full catalog with achievements in declared order`() = runTest {
        val repository = mockk<MilestoneRepository>()
        every { repository.getAchievements() } returns flowOf(
            listOf(
                MilestoneAchievement(
                    milestone = Milestone.WALKING_ALONE,
                    achievedOn = LocalDate.of(2026, 6, 1),
                ),
            ),
        )
        val useCase = GetMilestoneProgressUseCase(repository)

        useCase().test {
            val progress = awaitItem()
            assertEquals(Milestone.entries.toList(), progress.map { it.milestone })
            assertTrue(progress.first { it.milestone == Milestone.WALKING_ALONE }.isAchieved)
            assertFalse(progress.first { it.milestone == Milestone.SITTING_WITHOUT_SUPPORT }.isAchieved)
            awaitComplete()
        }
    }
}
