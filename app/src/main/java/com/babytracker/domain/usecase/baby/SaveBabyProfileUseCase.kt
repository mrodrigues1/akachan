package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import javax.inject.Inject

class SaveBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository
) {
    suspend operator fun invoke(baby: Baby) {
        require(baby.name.isNotBlank()) { "Baby name must not be blank" }
        babyRepository.saveBaby(baby)
    }
}
