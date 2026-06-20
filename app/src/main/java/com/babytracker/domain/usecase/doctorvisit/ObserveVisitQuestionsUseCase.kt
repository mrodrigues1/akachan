package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVisitQuestionsUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    operator fun invoke(visitId: Long): Flow<List<VisitQuestion>> = repository.observeQuestionsForVisit(visitId)
}
