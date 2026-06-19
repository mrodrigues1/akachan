package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import java.time.Instant
import javax.inject.Inject

class EditVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(record: VaccineRecord) {
        require(record.name.isNotBlank()) { "Vaccine name is required" }
        when (record.status) {
            VaccineStatus.ADMINISTERED -> require(
                record.administeredDate != null && !record.administeredDate.isAfter(now()),
            ) { "Administered date is required and cannot be in the future" }
            VaccineStatus.SCHEDULED -> require(record.scheduledDate != null) {
                "Scheduled date is required"
            }
        }
        val normalized = record.copy(
            name = record.name.trim(),
            doseLabel = record.doseLabel?.takeIf { it.isNotBlank() },
            notes = record.notes?.takeIf { it.isNotBlank() },
        )
        repository.update(normalized)
        // Best-effort: re-arming (or clearing) the reminder must never fail the local update.
        runCatching { reminderScheduler.schedule(normalized) }
    }
}
