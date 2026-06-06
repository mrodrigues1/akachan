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
        val baby = babyRepository.getBabyProfile().first() ?: return
        val existing = profileRepository.getProfile()
        if (existing != null && existing.dateOfBirth == baby.birthDate) return
        val nowMs = nowProvider().toEpochMilli()
        profileRepository.upsertProfile(
            BabyProfile(
                dateOfBirth = baby.birthDate,
                dueDate = existing?.dueDate,
                isDueDateUserProvided = existing?.isDueDateUserProvided ?: false,
                homeTimezoneId = existing?.homeTimezoneId ?: ZoneId.systemDefault().id,
                createdAtEpochMs = existing?.createdAtEpochMs ?: nowMs,
                updatedAtEpochMs = nowMs,
            )
        )
    }
}
