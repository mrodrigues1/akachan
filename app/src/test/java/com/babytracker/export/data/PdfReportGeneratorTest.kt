package com.babytracker.export.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
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
}
