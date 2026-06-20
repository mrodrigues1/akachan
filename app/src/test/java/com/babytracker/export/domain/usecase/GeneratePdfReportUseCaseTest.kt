package com.babytracker.export.domain.usecase

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.export.domain.model.DateRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GeneratePdfReportUseCaseTest {

    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var sleepRepository: SleepRepository
    private lateinit var diaperRepository: DiaperRepository
    private lateinit var vaccineRepository: VaccineRepository
    private lateinit var doctorVisitRepository: DoctorVisitRepository
    private lateinit var renderer: PdfReportRenderer
    private lateinit var useCase: GeneratePdfReportUseCase

    @BeforeEach
    fun setup() {
        breastfeedingRepository = mockk()
        sleepRepository = mockk()
        diaperRepository = mockk()
        vaccineRepository = mockk()
        doctorVisitRepository = mockk()
        renderer = mockk()
        useCase = GeneratePdfReportUseCase(
            breastfeedingRepository, sleepRepository, diaperRepository, vaccineRepository,
            doctorVisitRepository, renderer,
        )
    }

    @Test
    fun `loads range from both repos and renders bytes`() = runTest {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val range = DateRange.lastDays(7, now)
        val sessions = listOf(
            BreastfeedingSession(
                id = 1, startTime = now.minusSeconds(3600), endTime = now.minusSeconds(2400),
                startingSide = BreastSide.LEFT,
            ),
        )
        coEvery { breastfeedingRepository.getCompletedSessionsBetween(range.start, range.end) } returns sessions
        coEvery { sleepRepository.getCompletedRecordsBetween(range.start, range.end) } returns emptyList()
        coEvery { diaperRepository.getBetween(range.start, range.end) } returns emptyList()
        coEvery { vaccineRepository.getAllOnce() } returns emptyList()
        coEvery { doctorVisitRepository.getAllVisitsOnce() } returns emptyList()

        val captured = slot<PdfReportData>()
        every { renderer.render(capture(captured)) } returns byteArrayOf(1, 2, 3)

        val result = useCase(range)

        assertEquals(listOf<Byte>(1, 2, 3), result.toList())
        assertEquals(sessions, captured.captured.breastfeeding)
        assertEquals(range, captured.captured.range)
    }
}
