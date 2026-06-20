package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class EditDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler
    private lateinit var useCase: EditDoctorVisitUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = EditDoctorVisitUseCase(repository, scheduler)
    }

    @Test
    fun `reconciles attachments atomically then re-arms`() = runTest {
        val visit = DoctorVisit(id = 4, date = Instant.ofEpochMilli(9_000), createdAt = Instant.ofEpochMilli(1))
        useCase(visit, attachQuestionIds = listOf(7))
        coVerifyOrder {
            repository.updateVisitReconcilingAttachments(any(), listOf(7))
            scheduler.cancel(4)
            scheduler.schedule(any())
        }
    }
}
