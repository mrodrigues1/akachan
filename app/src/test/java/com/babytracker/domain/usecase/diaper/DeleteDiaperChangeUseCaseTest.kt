package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteDiaperChangeUseCaseTest {
    @Test
    fun `delegates to repository`() = runTest {
        val repository = mockk<DiaperRepository>(relaxed = true)
        DeleteDiaperChangeUseCase(repository)(5)
        coVerify { repository.deleteById(5) }
    }
}
