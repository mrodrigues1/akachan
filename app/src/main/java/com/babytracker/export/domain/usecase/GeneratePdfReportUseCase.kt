package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.export.domain.model.DateRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GeneratePdfReportUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val diaperRepository: DiaperRepository,
    private val renderer: PdfReportRenderer,
) {
    suspend operator fun invoke(range: DateRange): ByteArray {
        val data = PdfReportData(
            range = range,
            breastfeeding = breastfeedingRepository.getCompletedSessionsBetween(range.start, range.end),
            sleep = sleepRepository.getCompletedRecordsBetween(range.start, range.end),
            diapers = diaperRepository.getBetween(range.start, range.end),
        )
        // Canvas/PDF rendering is synchronous CPU work — move off Main-dispatched ViewModel coroutine.
        return withContext(Dispatchers.Default) { renderer.render(data) }
    }
}
