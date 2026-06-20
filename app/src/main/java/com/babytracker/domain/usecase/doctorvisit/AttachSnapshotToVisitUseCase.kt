package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.repository.DoctorVisitRepository
import java.time.Instant
import javax.inject.Inject

class AttachSnapshotToVisitUseCase @Inject constructor(
    private val repository: DoctorVisitRepository,
) {
    suspend operator fun invoke(visitId: Long, label: String, now: Instant = Instant.now()) {
        val visit = repository.getVisitById(visitId) ?: return
        repository.updateVisit(visit.copy(snapshotLabel = label.trim(), snapshotCreatedAt = now))
    }
}
