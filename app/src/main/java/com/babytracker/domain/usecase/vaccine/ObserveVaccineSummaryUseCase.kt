package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.model.VaccineSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ObserveVaccineSummaryUseCase @Inject constructor(
    private val observeVaccineRecords: ObserveVaccineRecordsUseCase,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<VaccineSummary> =
        observeVaccineRecords().map { records ->
            val current = now()
            val scheduled = records.filter {
                it.status == VaccineStatus.SCHEDULED && it.scheduledDate != null
            }
            val future = scheduled.filter { !it.scheduledDate!!.isBefore(current) }
            val overdue = scheduled.filter { it.scheduledDate!!.isBefore(current) }
            val administered = records.filter { it.status == VaccineStatus.ADMINISTERED }
            VaccineSummary(
                nextUpcoming = future.minByOrNull { it.scheduledDate!! },
                upcomingCount = future.size,
                overdueCount = overdue.size,
                administeredCount = administered.size,
                lastAdministered = administered.maxByOrNull { it.administeredDate ?: it.createdAt },
            )
        }
}
