package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import java.time.Instant
import javax.inject.Inject

class AddDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
) {
    suspend operator fun invoke(
        date: Instant,
        providerName: String?,
        notes: String?,
        attachQuestionIds: List<Long> = emptyList(),
        now: Instant = Instant.now(),
    ): Long {
        val visit = DoctorVisit(
            date = date,
            providerName = providerName?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            createdAt = now,
        )
        val id = repository.insertVisitWithAttachments(visit, attachQuestionIds) // atomic insert + attach
        reminderScheduler.schedule(visit.copy(id = id))
        return id
    }
}
