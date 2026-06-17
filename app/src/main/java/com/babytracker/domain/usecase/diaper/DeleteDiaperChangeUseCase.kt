package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import javax.inject.Inject

class DeleteDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteById(id)
}
