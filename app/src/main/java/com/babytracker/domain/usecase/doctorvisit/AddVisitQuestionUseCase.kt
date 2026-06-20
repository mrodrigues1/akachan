package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import java.time.Instant
import javax.inject.Inject

class AddVisitQuestionUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(text: String, now: Instant = Instant.now()): Long {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Question text must not be blank" }
        return repository.insertQuestion(
            VisitQuestion(text = trimmed, answered = false, visitId = null, createdAt = now),
        )
    }
}
