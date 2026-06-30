package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import java.time.LocalDate
import javax.inject.Inject

class SaveBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository,
    private val bootstrapBabyProfile: BootstrapBabyProfileUseCase,
) {
    suspend operator fun invoke(baby: Baby, userDueDate: LocalDate? = null) {
        require(baby.name.isNotBlank()) { "Baby name must not be blank" }
        require(!baby.birthDate.isAfter(LocalDate.now())) { "Birth date cannot be in the future" }
        if (userDueDate != null) {
            // A user-entered due date is unrecoverable input. Persist it (separate Room store) BEFORE
            // saveBabyProfile, which atomically flips onboarding_complete: a crash between the two
            // writes would otherwise mark onboarding done while the due date is still only in memory,
            // and the next launch would skip onboarding and lose it. Failures propagate so onboarding
            // stays retryable.
            bootstrapBabyProfile(userDueDate, baby.birthDate)
            babyRepository.saveBabyProfile(baby)
        } else {
            babyRepository.saveBabyProfile(baby)
            // No due date: bootstrap is best-effort and re-runs on next launch.
            runCatching { bootstrapBabyProfile() }
        }
    }
}
