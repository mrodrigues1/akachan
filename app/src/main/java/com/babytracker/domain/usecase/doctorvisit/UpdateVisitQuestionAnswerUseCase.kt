package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import javax.inject.Inject

/**
 * Records the doctor's free-text answer to a pre-visit question (issue #792).
 *
 * The answer is optional and opt-in. A non-blank answer auto-checks the question (`answered = true`);
 * clearing the answer keeps `answered` as-is (unchecking is a separate, independent action). The
 * stored answer is trimmed; a blank answer persists as `null`.
 */
class UpdateVisitQuestionAnswerUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(id: Long, answer: String) {
        val question = repository.getQuestionById(id) ?: return
        val trimmed = answer.trim().ifEmpty { null }
        repository.updateQuestion(
            question.copy(
                answer = trimmed,
                answered = if (trimmed != null) true else question.answered,
            ),
        )
    }
}
