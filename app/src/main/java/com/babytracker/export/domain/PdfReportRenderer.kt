package com.babytracker.export.domain

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.export.domain.model.DateRange

data class PdfReportData(
    val range: DateRange,
    val breastfeeding: List<BreastfeedingSession>,
    val sleep: List<SleepRecord>,
)

/** Renders the report to PDF bytes. Implemented in export/data (Android Canvas). */
interface PdfReportRenderer {
    fun render(data: PdfReportData): ByteArray
}
