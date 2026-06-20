package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.VaccineRepository
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
    private val vaccineRepository: VaccineRepository,
    private val doctorVisitRepository: DoctorVisitRepository,
    private val renderer: PdfReportRenderer,
) {
    suspend operator fun invoke(range: DateRange): ByteArray {
        val data = PdfReportData(
            range = range,
            breastfeeding = breastfeedingRepository.getCompletedSessionsBetween(range.start, range.end),
            sleep = sleepRepository.getCompletedRecordsBetween(range.start, range.end),
            diapers = diaperRepository.getBetween(range.start, range.end),
            // Vaccines are an immunization record, not a windowed event stream: show the full
            // administered history and upcoming schedule regardless of the report's date range.
            vaccines = vaccineRepository.getAllOnce(),
            // Doctor visits are a reference log (past + upcoming), shown in full regardless of range.
            doctorVisits = doctorVisitRepository.getAllVisitsOnce(),
        )
        // Canvas/PDF rendering is synchronous CPU work — move off Main-dispatched ViewModel coroutine.
        return withContext(Dispatchers.Default) { renderer.render(data) }
    }
}
