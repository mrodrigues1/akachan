package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDiaperChangesUseCase @Inject constructor(
    private val repository: DiaperRepository,
) {
    operator fun invoke(): Flow<List<DiaperChange>> = repository.observeAll()
}
