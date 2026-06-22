package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import javax.inject.Inject

/**
 * Reverts a just-marked dose back to its prior scheduled state, used by the dashboard's mark-given
 * undo. Writes [record] (the original scheduled record, held by the caller) straight back, then
 * re-arms the reminder that [MarkVaccineAdministeredUseCase] cancelled. Deliberately bypasses the
 * add/edit future-date validation so an overdue dose can be restored to its original past date.
 */
class UndoMarkVaccineAdministeredUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) {
    suspend operator fun invoke(record: VaccineRecord) {
        repository.update(record)
        // Best-effort: re-arming the reminder must never fail the local revert.
        runCatching { reminderScheduler.schedule(record) }
    }
}
