package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import javax.inject.Inject

class DeleteDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
    private val syncedWrite: SyncedWrite,
) {
    suspend operator fun invoke(id: Long) {
        syncedWrite(SyncType.FULL) {
            repository.deleteVisitDetachingQuestions(id) // atomic: detach questions + delete visit
            reminderScheduler.cancel(id) // alarm cleanup after DB success
        }
    }
}
