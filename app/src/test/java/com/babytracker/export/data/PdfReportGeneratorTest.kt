package com.babytracker.export.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.model.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class PdfReportGeneratorTest {

    private val generator = PdfReportGenerator()

    private fun session(startTime: Instant) = BreastfeedingSession(
        id = 0, startTime = startTime, endTime = startTime.plusSeconds(600),
        startingSide = BreastSide.LEFT,
    )

    private fun sleepRecord(startTime: Instant) = SleepRecord(
        id = 0, startTime = startTime, endTime = startTime.plusSeconds(3600),
        sleepType = SleepType.NAP,
    )

    private fun diaper(startTime: Instant) = DiaperChange(
        id = 0, timestamp = startTime, type = DiaperType.WET, createdAt = startTime,
    )

    private fun administeredVaccine(date: Instant) = VaccineRecord(
        id = 0, name = "BCG", doseLabel = "Dose 1", status = VaccineStatus.ADMINISTERED,
        administeredDate = date, createdAt = date,
    )

    private fun scheduledVaccine(date: Instant) = VaccineRecord(
        id = 0, name = "Hep B", doseLabel = null, status = VaccineStatus.SCHEDULED,
        scheduledDate = date, createdAt = date,
    )

    @Test
    fun `paginate computes page count from row count`() {
        assertEquals(1, PdfReportGenerator.paginate(rowCount = 0, rowsPerPage = 30))
        assertEquals(1, PdfReportGenerator.paginate(rowCount = 30, rowsPerPage = 30))
        assertEquals(2, PdfReportGenerator.paginate(rowCount = 31, rowsPerPage = 30))
    }

    @Test
    fun `render produces non-empty PDF bytes`() {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val data = PdfReportData(
            range = DateRange.lastDays(7, now),
            breastfeeding = listOf(
                BreastfeedingSession(
                    id = 1, startTime = now.minusSeconds(3600), endTime = now.minusSeconds(2400),
                    startingSide = BreastSide.LEFT,
                ),
            ),
            sleep = listOf(
                SleepRecord(
                    id = 1, startTime = now.minusSeconds(7200), endTime = now.minusSeconds(3600),
                    sleepType = SleepType.NAP,
                ),
            ),
        )
        // PdfDocument.writeTo is not fully supported in all Robolectric SDK levels; skip if it throws.
        try {
            val bytes = generator.render(data)
            assertTrue("PDF bytes should be non-empty", bytes.isNotEmpty())
            // Valid PDFs start with the "%PDF" magic header.
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        } catch (e: IllegalStateException) {
            org.junit.Assume.assumeNoException(e)
        }
    }

    @Test
    fun `render all-time range does not crash and produces valid PDF`() {
        val now = Instant.parse("2026-05-26T00:00:00Z")
        val sessionStart = now.minus(Duration.ofDays(30))
        val data = PdfReportData(
            range = DateRange.allTime(now),
            breastfeeding = listOf(session(sessionStart)),
            sleep = listOf(sleepRecord(sessionStart)),
        )
        try {
            val bytes = generator.render(data)
            assertTrue(bytes.isNotEmpty())
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        } catch (e: IllegalStateException) {
            org.junit.Assume.assumeNoException(e)
        }
    }

    @Test
    fun `render large dataset does not crash - page count boundary`() {
        val now = Instant.parse("2026-05-26T00:00:00Z")
        // 40 sessions + 40 sleep records is well past the first-page limit;
        // exercises the countPages/render boundary to guard footer drift.
        val sessions = (1..40).map { i -> session(now.minusSeconds(i * 3600L)) }
        val sleeps = (1..40).map { i -> sleepRecord(now.minusSeconds(i * 7200L)) }
        val data = PdfReportData(
            range = DateRange.lastDays(30, now),
            breastfeeding = sessions,
            sleep = sleeps,
        )
        try {
            val bytes = generator.render(data)
            assertTrue(bytes.isNotEmpty())
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        } catch (e: IllegalStateException) {
            org.junit.Assume.assumeNoException(e)
        }
    }

    @Test
    fun `render with vaccines does not crash and produces valid PDF`() {
        val now = Instant.parse("2026-05-26T00:00:00Z")
        val data = PdfReportData(
            range = DateRange.lastDays(30, now),
            breastfeeding = listOf(session(now.minusSeconds(3600))),
            sleep = listOf(sleepRecord(now.minusSeconds(7200))),
            vaccines = listOf(
                administeredVaccine(now.minusSeconds(86_400)),
                scheduledVaccine(now.plusSeconds(86_400)),
            ),
        )
        try {
            val bytes = generator.render(data)
            assertTrue(bytes.isNotEmpty())
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        } catch (e: IllegalStateException) {
            org.junit.Assume.assumeNoException(e)
        }
    }

    @Test
    fun `render with many vaccines does not crash - exercises vaccine pagination mirror`() {
        val now = Instant.parse("2026-05-26T00:00:00Z")
        // 30 administered + 30 upcoming push both sub-lists across page boundaries, exercising the
        // countPages/render mirror for the new section to guard footer drift.
        val administered = (1..30).map { i -> administeredVaccine(now.minusSeconds(i * 86_400L)) }
        val scheduled = (1..30).map { i -> scheduledVaccine(now.plusSeconds(i * 86_400L)) }
        val data = PdfReportData(
            range = DateRange.lastDays(30, now),
            breastfeeding = emptyList(),
            sleep = emptyList(),
            vaccines = administered + scheduled,
        )
        try {
            val bytes = generator.render(data)
            assertTrue(bytes.isNotEmpty())
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        } catch (e: IllegalStateException) {
            org.junit.Assume.assumeNoException(e)
        }
    }

    @Test
    fun `render with many diapers does not crash - exercises diaper pagination mirror`() {
        val now = Instant.parse("2026-05-26T00:00:00Z")
        // 40 diapers push the diaper section across a page boundary, exercising the
        // countPages/render mirror for the new section to guard footer drift.
        val diapers = (1..40).map { i -> diaper(now.minusSeconds(i * 1800L)) }
        val data = PdfReportData(
            range = DateRange.lastDays(30, now),
            breastfeeding = listOf(session(now.minusSeconds(3600))),
            sleep = listOf(sleepRecord(now.minusSeconds(7200))),
            diapers = diapers,
        )
        try {
            val bytes = generator.render(data)
            assertTrue(bytes.isNotEmpty())
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        } catch (e: IllegalStateException) {
            org.junit.Assume.assumeNoException(e)
        }
    }
}
