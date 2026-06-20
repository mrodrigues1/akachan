package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import javax.inject.Inject

class EditDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun invoke(visit: DoctorVisit, attachQuestionIds: List<Long>) {
        val sanitized = visit.copy(
            providerName = visit.providerName?.trim()?.ifBlank { null },
            notes = visit.notes?.trim()?.ifBlank { null },
        )
        repository.updateVisitReconcilingAttachments(sanitized, attachQuestionIds) // atomic update + reconcile
        reminderScheduler.cancel(sanitized.id)
        reminderScheduler.schedule(sanitized)
        // Sync is best-effort: a partner-sync failure must never fail the local write.
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.FULL) }
    }
}
