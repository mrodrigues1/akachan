package com.babytracker.domain.usecase.milestone

import app.cash.turbine.test
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestoneUseCasesTest {

    private lateinit var repository: MilestoneRepository

    private val sample = Milestone(id = 1, title = "Smile", date = LocalDate.of(2026, 6, 1))

    @BeforeEach
    fun setup() {
        repository = mockk()
    }

    @Test
    fun `add delegates to repository and returns id`() = runTest {
        coEvery { repository.addMilestone(sample) } returns 42L
        assertEquals(42L, AddMilestoneUseCase(repository)(sample))
        coVerify { repository.addMilestone(sample) }
    }

    @Test
    fun `update delegates to repository`() = runTest {
        coEvery { repository.updateMilestone(sample) } just Runs
        UpdateMilestoneUseCase(repository)(sample)
        coVerify { repository.updateMilestone(sample) }
    }

    @Test
    fun `delete delegates to repository by id`() = runTest {
        coEvery { repository.deleteMilestone(1) } just Runs
        DeleteMilestoneUseCase(repository)(1)
        coVerify { repository.deleteMilestone(1) }
    }

    @Test
    fun `getMilestones returns the repository flow`() = runTest {
        every { repository.getMilestones() } returns flowOf(listOf(sample))
        GetMilestonesUseCase(repository)().test {
            assertEquals(listOf(sample), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `getMilestone returns the repository flow`() = runTest {
        every { repository.getMilestone(1) } returns flowOf(sample)
        GetMilestoneUseCase(repository)(1).test {
            assertEquals(sample, awaitItem())
            awaitComplete()
        }
    }
}
