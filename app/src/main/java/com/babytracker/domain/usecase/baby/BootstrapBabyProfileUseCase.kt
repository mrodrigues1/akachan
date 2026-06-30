package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class BootstrapBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository,
    private val profileRepository: BabyProfileRepository,
    private val nowProvider: () -> Instant,
) {
    /**
     * Ensures a [BabyProfile] exists for the saved baby. Pass [userDueDate] when the user
     * explicitly entered a due date (e.g. a preterm baby during onboarding) — it is stored
     * and flagged as user-provided so corrected-age calculations use it. A non-null
     * [userDueDate] also forces an update even when a matching profile already exists.
     *
     * Pass [birthDate] to persist the profile before the baby is saved (onboarding writes the
     * due date first so it is durable before `saveBabyProfile` flips `onboarding_complete`);
     * when null the birth date is read from the already-saved baby.
     */
    suspend operator fun invoke(userDueDate: LocalDate? = null, birthDate: LocalDate? = null) {
        val resolvedBirthDate = birthDate ?: babyRepository.getBabyProfile().first()?.birthDate ?: return
        val existing = profileRepository.getProfile()
        if (existing != null && existing.dateOfBirth == resolvedBirthDate && userDueDate == null) return
        val nowMs = nowProvider().toEpochMilli()
        profileRepository.upsertProfile(
            BabyProfile(
                dateOfBirth = resolvedBirthDate,
                dueDate = userDueDate ?: existing?.dueDate,
                isDueDateUserProvided = userDueDate != null || (existing?.isDueDateUserProvided ?: false),
                homeTimezoneId = existing?.homeTimezoneId ?: ZoneId.systemDefault().id,
                createdAtEpochMs = existing?.createdAtEpochMs ?: nowMs,
                updatedAtEpochMs = nowMs,
            )
        )
    }
}
