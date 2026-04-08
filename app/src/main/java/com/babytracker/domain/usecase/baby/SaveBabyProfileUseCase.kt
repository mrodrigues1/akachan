package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import java.time.LocalDate
import javax.inject.Inject

class SaveBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository
) {
    suspend operator fun invoke(baby: Baby) {
        require(baby.name.isNotBlank()) { "Baby name must not be blank" }
        require(!baby.birthDate.isAfter(LocalDate.now())) { "Birth date cannot be in the future" }
        babyRepository.saveBabyProfile(baby)
    }
}
