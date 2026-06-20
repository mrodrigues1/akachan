package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.isUpcoming
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

class ObserveDoctorVisitSummaryUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<DoctorVisitSummary> =
        combine(
            repository.observeAllVisits(),
            repository.observeInboxQuestions(),
        ) { visits, inbox ->
            val instant = now()
            val upcoming = visits.filter { it.isUpcoming(instant) }.minByOrNull { it.date }
            val past = visits.filterNot { it.isUpcoming(instant) }.maxByOrNull { it.date }
            DoctorVisitSummary(
                nextUpcoming = upcoming,
                lastPast = past,
                openQuestionCount = inbox.count { !it.answered },
            )
        }
}
