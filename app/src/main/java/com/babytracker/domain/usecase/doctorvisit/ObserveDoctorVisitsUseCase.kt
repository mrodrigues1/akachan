package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDoctorVisitsUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    operator fun invoke(): Flow<List<DoctorVisit>> = repository.observeAllVisits()
}
