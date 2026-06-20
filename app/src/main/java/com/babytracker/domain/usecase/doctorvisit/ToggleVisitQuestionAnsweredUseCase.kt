package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import javax.inject.Inject

class ToggleVisitQuestionAnsweredUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(id: Long) {
        val q = repository.getQuestionById(id) ?: return
        repository.updateQuestion(q.copy(answered = !q.answered))
    }
}
