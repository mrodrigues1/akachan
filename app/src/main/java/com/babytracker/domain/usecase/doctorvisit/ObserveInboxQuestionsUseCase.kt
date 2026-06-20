package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInboxQuestionsUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    operator fun invoke(): Flow<List<VisitQuestion>> = repository.observeInboxQuestions()
}
