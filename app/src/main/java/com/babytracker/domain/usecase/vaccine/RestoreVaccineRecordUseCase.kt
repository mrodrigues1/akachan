package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import javax.inject.Inject

/**
 * Re-inserts a just-deleted record, used by the delete undo. Writes [record] back verbatim — Room's
 * autoGenerate keeps the original (non-zero) id, so the row returns with its identity and ordering
 * intact — then re-arms its reminder. Deliberately bypasses the add/edit future-date validation so an
 * overdue scheduled dose can be restored to its original past date.
 */
class RestoreVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) {
    suspend operator fun invoke(record: VaccineRecord) {
        repository.insert(record)
        // Best-effort: re-arming the reminder must never fail the local restore.
        runCatching { reminderScheduler.schedule(record) }
    }
}
