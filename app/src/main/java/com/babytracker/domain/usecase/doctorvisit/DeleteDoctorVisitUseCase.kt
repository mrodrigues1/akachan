package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import javax.inject.Inject

class DeleteDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteVisitDetachingQuestions(id) // atomic: detach questions + delete visit
        reminderScheduler.cancel(id) // alarm cleanup after DB success
        // Sync is best-effort: a partner-sync failure must never fail the local write.
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.FULL) }
    }
}
