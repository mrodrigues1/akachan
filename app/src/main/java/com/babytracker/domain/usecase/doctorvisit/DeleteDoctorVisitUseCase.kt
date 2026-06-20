package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import javax.inject.Inject

class DeleteDoctorVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val reminderScheduler: DoctorVisitReminderScheduler,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteVisitDetachingQuestions(id) // atomic: detach questions + delete visit
        reminderScheduler.cancel(id) // alarm cleanup after DB success
    }
}
