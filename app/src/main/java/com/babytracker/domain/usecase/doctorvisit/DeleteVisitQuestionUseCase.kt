package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import javax.inject.Inject

class DeleteVisitQuestionUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteQuestionById(id)
}
