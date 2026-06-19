package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.repository.VaccineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVaccineRecordsUseCase @Inject constructor(
    private val repository: VaccineRepository,
) {
    operator fun invoke(): Flow<List<VaccineRecord>> = repository.observeAll()
}
