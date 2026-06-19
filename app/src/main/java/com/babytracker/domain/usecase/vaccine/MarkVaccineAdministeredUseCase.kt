package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import java.time.Instant
import javax.inject.Inject

class MarkVaccineAdministeredUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(id: Long, administeredDate: Instant = now()) {
        require(!administeredDate.isAfter(now())) { "Administered date cannot be in the future" }
        val existing = repository.getById(id) ?: return
        // Only the scheduled->administered transition writes: guarding against an already-administered
        // record keeps this idempotent under double-taps / stale UI and prevents overwriting the
        // original administeredDate (user data corruption). Editing an administered record's date is
        // the EditVaccineRecordUseCase's job.
        if (existing.status == VaccineStatus.SCHEDULED) {
            repository.update(
                existing.copy(status = VaccineStatus.ADMINISTERED, administeredDate = administeredDate),
            )
        }
        // Always cancel (best-effort, idempotent): heals a stale alarm left by a prior partial
        // failure / import / drift even when the record is already administered.
        runCatching { reminderScheduler.cancel(id) }
    }
}
