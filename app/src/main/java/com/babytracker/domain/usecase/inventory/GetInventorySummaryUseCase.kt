package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInventorySummaryUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    operator fun invoke(): Flow<InventorySummary> = repository.getSummary()
}
