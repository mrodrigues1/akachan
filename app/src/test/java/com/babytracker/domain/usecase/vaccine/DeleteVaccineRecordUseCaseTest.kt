package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteVaccineRecordUseCaseTest {
    @Test
    fun `deletes and cancels reminder`() = runTest {
        val repository = mockk<VaccineRepository>(relaxed = true)
        val scheduler = mockk<VaccineReminderScheduler>(relaxed = true)
        DeleteVaccineRecordUseCase(repository, scheduler)(5)
        coVerify { repository.deleteById(5) }
        coVerify { scheduler.cancel(5) }
    }
}
