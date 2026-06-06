package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class BootstrapBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository,
    private val profileRepository: BabyProfileRepository,
    private val nowProvider: () -> Instant,
) {
    suspend operator fun invoke() {
        if (profileRepository.getProfile() != null) return
        val baby = babyRepository.getBabyProfile().first() ?: return
        val nowMs = nowProvider().toEpochMilli()
        profileRepository.upsertProfile(
            BabyProfile(
                dateOfBirth = baby.birthDate,
                dueDate = null,
                isDueDateUserProvided = false,
                homeTimezoneId = ZoneId.systemDefault().id,
                createdAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
            )
        )
    }
}
