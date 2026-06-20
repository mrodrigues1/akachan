package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.export.domain.model.DateRange
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import javax.inject.Inject

/**
 * Re-generates a fresh data snapshot on demand when the user taps "View snapshot" on a visit.
 * Pure delegation to the existing export pipeline — no snapshot file is persisted on the visit.
 * Defaults to the full-history range; the caller (plan 5) may pass an explicit range.
 */
class GenerateVisitSnapshotUseCase @Inject constructor(
    private val generatePdfReport: GeneratePdfReportUseCase,
) {
    suspend operator fun invoke(range: DateRange = DateRange.allTime()): ByteArray = generatePdfReport(range)
}
