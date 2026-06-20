package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
    }

    @Test
    fun `deletes atomically then cancels reminder`() = runTest {
        DeleteDoctorVisitUseCase(repository, scheduler)(6)
        coVerifyOrder {
            repository.deleteVisitDetachingQuestions(6)
            // cancel happens after the DB mutation
        }
        verify { scheduler.cancel(6) }
    }
}
