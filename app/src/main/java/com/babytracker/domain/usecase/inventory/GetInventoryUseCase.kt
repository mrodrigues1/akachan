package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInventoryUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    operator fun invoke(): Flow<List<MilkBag>> = repository.getActiveBags()
}
