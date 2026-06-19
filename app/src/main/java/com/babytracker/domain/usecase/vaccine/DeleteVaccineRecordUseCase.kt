package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import javax.inject.Inject

class DeleteVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteById(id)
        // Best-effort: cancelling the reminder must never fail the delete.
        runCatching { reminderScheduler.cancel(id) }
    }
}
