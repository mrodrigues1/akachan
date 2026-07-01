package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import javax.inject.Inject

class EditDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
    private val syncedWrite: SyncedWrite,
) {
    suspend operator fun invoke(visit: DoctorVisit, attachQuestionIds: List<Long>) {
        val sanitized = visit.copy(
            providerName = visit.providerName?.trim()?.ifBlank { null },
            notes = visit.notes?.trim()?.ifBlank { null },
        )
        syncedWrite(SyncType.FULL) {
            repository.updateVisitReconcilingAttachments(sanitized, attachQuestionIds) // atomic update + reconcile
            reminderScheduler.cancel(sanitized.id)
            reminderScheduler.schedule(sanitized)
        }
    }
}
