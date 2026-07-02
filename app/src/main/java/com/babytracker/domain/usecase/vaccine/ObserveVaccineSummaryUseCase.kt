package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.model.VaccineSummary
import com.babytracker.domain.model.isOverdue
import com.babytracker.domain.repository.VaccineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ObserveVaccineSummaryUseCase @Inject constructor(
    private val vaccineRepository: VaccineRepository,
    private val zone: ZoneId,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<VaccineSummary> =
        vaccineRepository.observeAll().map { records ->
            val current = now()
            val scheduled = records.filter {
                it.status == VaccineStatus.SCHEDULED && it.scheduledDate != null
            }
            // Day-based overdue: a dose due today counts as upcoming, not overdue, on the home tile too.
            val overdue = scheduled.filter { it.isOverdue(current, zone) }
            val future = scheduled.filterNot { it.isOverdue(current, zone) }
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
