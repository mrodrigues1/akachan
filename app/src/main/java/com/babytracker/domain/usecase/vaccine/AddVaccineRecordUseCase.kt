package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import java.time.Instant
import javax.inject.Inject

class AddVaccineRecordUseCase @Inject constructor(
    private val repository: VaccineRepository,
    private val reminderScheduler: VaccineReminderScheduler,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        name: String,
        doseLabel: String?,
        status: VaccineStatus,
        date: Instant,
        notes: String? = null,
    ): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Vaccine name is required" }
        val record = when (status) {
            VaccineStatus.ADMINISTERED -> {
                // A future "given" date is allowed (the parent may pre-log an appointment they just
                // attended whose date is technically ahead of the clock, or a forward-dated entry);
                // the UI warns about it rather than blocking the save.
                VaccineRecord(
                    name = trimmedName,
                    doseLabel = doseLabel?.takeIf { it.isNotBlank() },
                    status = status,
                    administeredDate = date,
                    notes = notes?.takeIf { it.isNotBlank() },
                    createdAt = now(),
                )
            }
            VaccineStatus.SCHEDULED -> {
                require(date.isAfter(now())) { "Scheduled date must be in the future" }
                VaccineRecord(
                    name = trimmedName,
                    doseLabel = doseLabel?.takeIf { it.isNotBlank() },
                    status = status,
                    scheduledDate = date,
                    notes = notes?.takeIf { it.isNotBlank() },
                    createdAt = now(),
                )
            }
            VaccineStatus.TO_SCHEDULE -> {
                // A to-schedule dose has a known target date but no booked appointment yet. The target
                // is stored in scheduledDate (status distinguishes the two) and, like a scheduled dose,
                // must be in the future so a reminder window exists.
                require(date.isAfter(now())) { "Scheduled date must be in the future" }
                VaccineRecord(
                    name = trimmedName,
                    doseLabel = doseLabel?.takeIf { it.isNotBlank() },
                    status = status,
                    scheduledDate = date,
                    notes = notes?.takeIf { it.isNotBlank() },
                    createdAt = now(),
                )
            }
        }
        val id = repository.insert(record)
        // Reminder scheduling is best-effort: a scheduler failure must never fail the local save
        // (otherwise a retry would duplicate the row).
        runCatching { reminderScheduler.schedule(record.copy(id = id)) }
        return id
    }
}
