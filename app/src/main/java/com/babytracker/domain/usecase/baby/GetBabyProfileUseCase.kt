package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBabyProfileUseCase @Inject constructor(
    private val babyRepository: BabyRepository
) {
    operator fun invoke(): Flow<Baby?> = babyRepository.getBabyProfile()
}
