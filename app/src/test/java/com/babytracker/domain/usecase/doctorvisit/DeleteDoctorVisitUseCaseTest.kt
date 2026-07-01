package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
    }

    @Test
    fun `deletes atomically then cancels reminder and syncs`() = runTest {
        DeleteDoctorVisitUseCase(repository, scheduler, SyncedWrite(syncToFirestore))(6)
        coVerifyOrder {
            repository.deleteVisitDetachingQuestions(6)
            // cancel happens after the DB mutation
        }
        verify { scheduler.cancel(6) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.FULL) }
    }
}
