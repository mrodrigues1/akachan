package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import javax.inject.Inject

/**
 * Flips a TO_SCHEDULE dose to SCHEDULED in one tap, leaving its date untouched (the target date
 * becomes the appointment date). Guards on the current status so it is idempotent under double-taps
 * and a no-op for already-scheduled / administered / missing records. Re-arms the reminder so it now
 * fires under the appointment lead instead of the to-schedule lead.
 */
class MarkVaccineScheduledUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
) {
    suspend operator fun invoke(id: Long) {
        val existing = repository.getById(id) ?: return
        if (existing.status != VaccineStatus.TO_SCHEDULE) return
        val updated = existing.copy(status = VaccineStatus.SCHEDULED)
        repository.update(updated)
        // Best-effort: re-arming must never fail the local flip.
        runCatching { reminderScheduler.schedule(updated) }
    }
}
