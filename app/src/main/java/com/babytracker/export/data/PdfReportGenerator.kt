package com.babytracker.export.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.util.formatPdfDateTime
import com.babytracker.util.formatDuration
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportGenerator @Inject constructor() : PdfReportRenderer {

    override fun render(data: PdfReportData): ByteArray {
        val totalPages = countPages(data)
        val doc = PdfDocument()
        var page = doc.startPage(newPageInfo(1))
        var canvas = page.canvas
        var y = MARGIN + BRAND_LABEL_SIZE
        canvas.drawText("AKACHAN", MARGIN, y, brandLabelPaint)
        y += LINE * 0.5f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, brandRulePaint)
        y += TITLE_SIZE + LINE * 0.2f

        canvas.drawText("Baby Health Summary", MARGIN, y, titlePaint)
        y += LINE
        val startLabel = if (data.range.start == Instant.EPOCH) "All time" else data.range.start.formatPdfDateTime()
        canvas.drawText(
            "$startLabel  –  ${data.range.end.formatPdfDateTime()}",
            MARGIN, y, captionPaint,
        )
        y += SECTION_GAP

        y = drawSummary(canvas, y, data)
        y += SECTION_GAP

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
        y += SECTION_GAP

        // Feeding section
        val feedPos = ensureRoom(doc, page, y, totalPages)
        page = feedPos.page; canvas = feedPos.canvas; y = feedPos.y
        canvas.drawText("Feeding (${data.breastfeeding.size})", MARGIN, y, feedingHeaderPaint)
        y += LINE * 0.7f
        y = drawColumnHeaders(canvas, y, "Date & Time", "Side", "Duration")
        canvas.drawLine(MARGIN, y - LINE * 0.15f, PAGE_WIDTH - MARGIN, y - LINE * 0.15f, separatorPaint)
        y += LINE * 0.5f

        for (s in data.breastfeeding) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                var hy = drawColumnHeaders(c, MARGIN + LINE, "Date & Time", "Side", "Duration")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.5f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawFeedingRow(canvas, y, s)
        }
        y += SECTION_GAP

        // Sleep section
        val sleepPos = ensureRoom(doc, page, y, totalPages)
        page = sleepPos.page; canvas = sleepPos.canvas; y = sleepPos.y
        canvas.drawText("Sleep (${data.sleep.size})", MARGIN, y, sleepHeaderPaint)
        y += LINE * 0.7f
        y = drawColumnHeaders(canvas, y, "Date & Time", "Type", "Duration")
        canvas.drawLine(MARGIN, y - LINE * 0.15f, PAGE_WIDTH - MARGIN, y - LINE * 0.15f, separatorPaint)
        y += LINE * 0.5f

        for (r in data.sleep) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                var hy = drawColumnHeaders(c, MARGIN + LINE, "Date & Time", "Type", "Duration")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.5f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawSleepRow(canvas, y, r)
        }
        y += SECTION_GAP

        val diaperPos = drawDiaperSection(doc, PagePos(page, canvas, y), totalPages, data.diapers)
        val vaxStart = PagePos(diaperPos.page, diaperPos.canvas, diaperPos.y + SECTION_GAP)
        val vaxPos = drawVaccinesSection(doc, vaxStart, totalPages, data.vaccines)
        val visitStart = PagePos(vaxPos.page, vaxPos.canvas, vaxPos.y + SECTION_GAP)
        page = drawDoctorVisitsSection(doc, visitStart, totalPages, data.doctorVisits).page

        drawPageFooter(page.canvas, page.info.pageNumber, totalPages)
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawSummary(canvas: android.graphics.Canvas, startY: Float, data: PdfReportData): Float {
        // For all-time reports (start == EPOCH), compute the window from the earliest actual
        // record so the per-day average is meaningful rather than ~0 over 20k days.
        val effectiveStart = if (data.range.start == Instant.EPOCH) {
            (data.breastfeeding.map { it.startTime } + data.sleep.map { it.startTime })
                .minOrNull() ?: data.range.end
        } else {
            data.range.start
        }
        val rangeDays = Duration.between(effectiveStart, data.range.end).toDays().coerceAtLeast(1)
        val avgFeedPerDay = "%.1f".format(data.breastfeeding.size.toFloat() / rangeDays)
        val avgSleepPerDay = "%.1f".format(data.sleep.size.toFloat() / rangeDays)
        val avgDiaperPerDay = "%.1f".format(data.diapers.size.toFloat() / rangeDays)

        var y = startY

        canvas.drawText("Summary", MARGIN, y, sectionLabelPaint)
        y += LINE * 0.6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
        y += LINE

        canvas.drawText("Feeding sessions", MARGIN, y, bodyPaint)
        canvas.drawText(data.breastfeeding.size.toString(), COL_SUMMARY_COUNT, y, bodyBoldPaint)
        canvas.drawText("avg $avgFeedPerDay per day", COL_SUMMARY_AVG, y, captionPaint)
        y += LINE

        canvas.drawText("Sleep records", MARGIN, y, bodyPaint)
        canvas.drawText(data.sleep.size.toString(), COL_SUMMARY_COUNT, y, bodyBoldPaint)
        canvas.drawText("avg $avgSleepPerDay per day", COL_SUMMARY_AVG, y, captionPaint)
        y += LINE

        canvas.drawText("Diaper changes", MARGIN, y, bodyPaint)
        canvas.drawText(data.diapers.size.toString(), COL_SUMMARY_COUNT, y, bodyBoldPaint)
        canvas.drawText("avg $avgDiaperPerDay per day", COL_SUMMARY_AVG, y, captionPaint)
        y += LINE * 0.6f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, separatorPaint)
        return y
    }

    private fun drawColumnHeaders(
        canvas: android.graphics.Canvas,
        startY: Float,
        col1: String,
        col2: String,
        col3: String,
    ): Float {
        canvas.drawText(col1, MARGIN, startY, columnHeaderPaint)
        canvas.drawText(col2, COL_TYPE, startY, columnHeaderPaint)
        canvas.drawText(col3, COL_DURATION, startY, columnHeaderPaint)
        return startY + LINE
    }

    private fun drawFeedingRow(canvas: android.graphics.Canvas, startY: Float, s: BreastfeedingSession): Float {
        val duration = s.activeDuration?.formatDuration() ?: "—"
        val side = s.startingSide.name.lowercase().replaceFirstChar { it.uppercase() }
        canvas.drawText(s.startTime.formatPdfDateTime(), MARGIN, startY, bodyPaint)
        canvas.drawText(side, COL_TYPE, startY, bodyPaint)
        canvas.drawText(duration, COL_DURATION, startY, bodyPaint)
        return startY + LINE
    }

    private fun drawSleepRow(canvas: android.graphics.Canvas, startY: Float, r: SleepRecord): Float {
        val duration = r.duration?.formatDuration() ?: "—"
        val type = r.sleepType.name
            .lowercase()
            .split("_")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        canvas.drawText(r.startTime.formatPdfDateTime(), MARGIN, startY, bodyPaint)
        canvas.drawText(type, COL_TYPE, startY, bodyPaint)
        canvas.drawText(duration, COL_DURATION, startY, bodyPaint)
        return startY + LINE
    }

    /**
     * Renders the Diaper section (header + Date/Type/Notes columns + rows), paginating mid-list.
     * The y-advancement here MUST stay mirrored in [countPages].
     */
    private fun drawDiaperSection(
        doc: PdfDocument,
        start: PagePos,
        totalPages: Int,
        diapers: List<DiaperChange>,
    ): PagePos {
        var page = start.page
        var canvas = start.canvas
        var y = start.y

        val headerPos = ensureRoom(doc, page, y, totalPages)
        page = headerPos.page; canvas = headerPos.canvas; y = headerPos.y
        canvas.drawText("Diapers (${diapers.size})", MARGIN, y, diaperHeaderPaint)
        y += LINE * 0.7f
        y = drawColumnHeaders(canvas, y, "Date & Time", "Type", "Notes")
        canvas.drawLine(MARGIN, y - LINE * 0.15f, PAGE_WIDTH - MARGIN, y - LINE * 0.15f, separatorPaint)
        y += LINE * 0.5f

        for (d in diapers) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                val hy = drawColumnHeaders(c, MARGIN + LINE, "Date & Time", "Type", "Notes")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.5f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawDiaperRow(canvas, y, d)
        }
        return PagePos(page, canvas, y)
    }

    private fun drawDiaperRow(canvas: android.graphics.Canvas, startY: Float, d: DiaperChange): Float {
        val type = d.type.name.lowercase().replaceFirstChar { it.uppercase() }
        canvas.drawText(d.timestamp.formatPdfDateTime(), MARGIN, startY, bodyPaint)
        canvas.drawText(type, COL_TYPE, startY, bodyPaint)
        canvas.drawText(d.notes ?: "—", COL_DURATION, startY, bodyPaint)
        return startY + LINE
    }

    /**
     * Renders the whole Vaccines section: a coloured header plus an "Administered" and an "Upcoming"
     * sub-list. Splitting by status keeps the immunization history and the forward schedule visually
     * separate. The y-advancement here MUST stay mirrored in [countPages].
     */
    private fun drawVaccinesSection(
        doc: PdfDocument,
        start: PagePos,
        totalPages: Int,
        vaccines: List<VaccineRecord>,
    ): PagePos {
        val administered = vaccines
            .filter { it.status == VaccineStatus.ADMINISTERED }
            .sortedBy { it.administeredDate ?: it.createdAt }
        // "Upcoming" is every non-administered dose: scheduled appointments and to-schedule targets
        // alike (both carry scheduledDate). Folding to-schedule in here keeps the page-count mirror in
        // [countPages] a single filter and guarantees every vaccine lands in exactly one sublist.
        val upcoming = vaccines
            .filter { it.status != VaccineStatus.ADMINISTERED }
            .sortedBy { it.scheduledDate ?: it.createdAt }

        val headerPos = ensureRoom(doc, start.page, start.y, totalPages)
        headerPos.canvas.drawText("Vaccines (${vaccines.size})", MARGIN, headerPos.y, vaccineHeaderPaint)

        val admStart = PagePos(headerPos.page, headerPos.canvas, headerPos.y + LINE * 0.7f)
        val admPos = drawVaccineSubsection(doc, admStart, totalPages, "Administered", administered) {
            it.administeredDate
        }
        val upStart = PagePos(admPos.page, admPos.canvas, admPos.y + SECTION_GAP)
        return drawVaccineSubsection(doc, upStart, totalPages, "Upcoming", upcoming) {
            it.scheduledDate
        }
    }

    /**
     * Draws one labelled vaccine sub-list (header + Date/Vaccine/Dose columns + rows), paginating
     * mid-list like the other sections. [dateOf] selects which timestamp the row shows (administered
     * vs scheduled). Returns the cursor after the last row so the caller can continue laying out.
     * The y-advancement here MUST stay mirrored in [countPages].
     */
    private fun drawVaccineSubsection(
        doc: PdfDocument,
        start: PagePos,
        totalPages: Int,
        label: String,
        rows: List<VaccineRecord>,
        dateOf: (VaccineRecord) -> Instant?,
    ): PagePos {
        var page = start.page
        var canvas = start.canvas
        var y = start.y

        val labelPos = ensureRoom(doc, page, y, totalPages)
        page = labelPos.page; canvas = labelPos.canvas; y = labelPos.y
        canvas.drawText("$label (${rows.size})", MARGIN, y, sectionLabelPaint)
        y += LINE * 0.7f
        y = drawColumnHeaders(canvas, y, "Date", "Vaccine", "Dose")
        canvas.drawLine(MARGIN, y - LINE * 0.15f, PAGE_WIDTH - MARGIN, y - LINE * 0.15f, separatorPaint)
        y += LINE * 0.5f

        for (v in rows) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                val hy = drawColumnHeaders(c, MARGIN + LINE, "Date", "Vaccine", "Dose")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.5f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawVaccineRow(canvas, y, v, dateOf(v))
        }
        return PagePos(page, canvas, y)
    }

    private fun drawVaccineRow(
        canvas: android.graphics.Canvas,
        startY: Float,
        v: VaccineRecord,
        date: Instant?,
    ): Float {
        canvas.drawText(date?.formatPdfDateTime() ?: "—", MARGIN, startY, bodyPaint)
        canvas.drawText(v.name, COL_TYPE, startY, bodyPaint)
        canvas.drawText(v.doseLabel ?: "—", COL_DURATION, startY, bodyPaint)
        return startY + LINE
    }

    /**
     * Renders the Doctor visits section (header + Date/Provider/Notes columns + rows, most recent
     * first), paginating mid-list. The y-advancement here MUST stay mirrored in [countPages].
     */
    private fun drawDoctorVisitsSection(
        doc: PdfDocument,
        start: PagePos,
        totalPages: Int,
        visits: List<DoctorVisit>,
    ): PagePos {
        var page = start.page
        var canvas = start.canvas
        var y = start.y
        val sorted = visits.sortedByDescending { it.date }

        val headerPos = ensureRoom(doc, page, y, totalPages)
        page = headerPos.page; canvas = headerPos.canvas; y = headerPos.y
        canvas.drawText("Doctor visits (${visits.size})", MARGIN, y, doctorVisitHeaderPaint)
        y += LINE * 0.7f
        y = drawColumnHeaders(canvas, y, "Date", "Provider", "Notes")
        canvas.drawLine(MARGIN, y - LINE * 0.15f, PAGE_WIDTH - MARGIN, y - LINE * 0.15f, separatorPaint)
        y += LINE * 0.5f

        for (v in sorted) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                val hy = drawColumnHeaders(c, MARGIN + LINE, "Date", "Provider", "Notes")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.5f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawDoctorVisitRow(canvas, y, v)
        }
        return PagePos(page, canvas, y)
    }

    private fun drawDoctorVisitRow(canvas: android.graphics.Canvas, startY: Float, v: DoctorVisit): Float {
        canvas.drawText(v.date.formatPdfDateTime(), MARGIN, startY, bodyPaint)
        canvas.drawText(v.providerName?.takeIf { it.isNotBlank() } ?: "—", COL_TYPE, startY, bodyPaint)
        canvas.drawText(v.notes?.takeIf { it.isNotBlank() } ?: "—", COL_DURATION, startY, bodyPaint)
        return startY + LINE
    }

    private fun drawPageFooter(canvas: android.graphics.Canvas, pageNumber: Int, totalPages: Int) {
        canvas.drawText("Page $pageNumber of $totalPages", PAGE_WIDTH / 2, PAGE_HEIGHT - MARGIN / 2, pageNumberPaint)
    }

    private data class PagePos(
        val page: PdfDocument.Page,
        val canvas: android.graphics.Canvas,
        val y: Float,
    )

    private fun ensureRoom(
        doc: PdfDocument,
        current: PdfDocument.Page,
        y: Float,
        totalPages: Int,
        onNewPage: ((android.graphics.Canvas) -> Float)? = null,
    ): PagePos {
        if (y <= PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) return PagePos(current, current.canvas, y)
        drawPageFooter(current.canvas, current.info.pageNumber, totalPages)
        doc.finishPage(current)
        val page = doc.startPage(newPageInfo(current.info.pageNumber + 1))
        val newY = onNewPage?.invoke(page.canvas) ?: (MARGIN + LINE)
        return PagePos(page, page.canvas, newY)
    }

    /**
     * Simulates the layout pass to count total pages without drawing.
     * Must mirror the y-advancement logic in render() exactly.
     */
    private fun countPages(data: PdfReportData): Int {
        var pageCount = 1

        fun sim(y: Float, newPageY: Float): Float =
            if (y <= PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) y else { pageCount++; newPageY }

        var y = MARGIN + BRAND_LABEL_SIZE + LINE * 0.5f + TITLE_SIZE + LINE * 0.2f + LINE + SECTION_GAP
        y += LINE * 0.6f + LINE + LINE + LINE + LINE * 0.6f  // mirrors drawSummary (feed, sleep, diaper)
        y += SECTION_GAP
        y += SECTION_GAP  // after separator

        // Feeding section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f

        repeat(data.breastfeeding.size) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }
        y += SECTION_GAP

        // Sleep section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f

        repeat(data.sleep.size) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }
        y += SECTION_GAP

        // Diaper section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f

        repeat(data.diapers.size) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }
        y += SECTION_GAP

        // Vaccines section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f

        // Administered subsection (label + column headers + rows)
        val administeredCount = data.vaccines.count { it.status == VaccineStatus.ADMINISTERED }
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f
        repeat(administeredCount) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }
        y += SECTION_GAP

        // Upcoming subsection (label + column headers + rows)
        val upcomingCount = data.vaccines.count { it.status != VaccineStatus.ADMINISTERED }
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f
        repeat(upcomingCount) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }
        y += SECTION_GAP

        // Doctor visits section (header + column headers + rows)
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f
        repeat(data.doctorVisits.size) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }

        return pageCount
    }

    private fun newPageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create()

    private val brandLabelPaint = Paint().apply {
        color = FEEDING
        textSize = BRAND_LABEL_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.12f
    }
    private val brandRulePaint = Paint().apply {
        color = FEEDING
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val titlePaint = Paint().apply {
        color = ON_SURFACE
        textSize = TITLE_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val captionPaint = Paint().apply {
        color = ON_SURFACE_VARIANT
        textSize = CAPTION_SIZE
    }
    private val sectionLabelPaint = Paint().apply {
        color = ON_SURFACE_VARIANT
        textSize = CAPTION_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.06f
    }
    private val feedingHeaderPaint = Paint().apply {
        color = FEEDING
        textSize = HEADER_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val sleepHeaderPaint = Paint().apply {
        color = SLEEP
        textSize = HEADER_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val diaperHeaderPaint = Paint().apply {
        color = DIAPER
        textSize = HEADER_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val vaccineHeaderPaint = Paint().apply {
        color = VACCINE
        textSize = HEADER_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val doctorVisitHeaderPaint = Paint().apply {
        color = DOCTOR_VISIT
        textSize = HEADER_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bodyPaint = Paint().apply {
        color = ON_SURFACE
        textSize = BODY_SIZE
    }
    private val bodyBoldPaint = Paint().apply {
        color = ON_SURFACE
        textSize = BODY_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val columnHeaderPaint = Paint().apply {
        color = ON_SURFACE_VARIANT
        textSize = CAPTION_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val separatorPaint = Paint().apply {
        color = OUTLINE_COLOR
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }
    private val pageNumberPaint = Paint().apply {
        color = ON_SURFACE_VARIANT
        textSize = CAPTION_SIZE
        textAlign = Paint.Align.CENTER
    }

    companion object {
        // Design tokens re-declared as android.graphics.Color ints (cannot import Compose Color).
        private const val FEEDING = 0xFFC2185B.toInt()            // Pink700
        private const val SLEEP = 0xFF1976D2.toInt()              // Blue700
        private const val DIAPER = 0xFF00897B.toInt()            // Teal600
        private const val VACCINE = 0xFF303F9F.toInt()           // Indigo700
        private const val DOCTOR_VISIT = 0xFF455A64.toInt()      // BlueGrey700 (DoctorSlate)
        private const val ON_SURFACE = 0xFF1A1A1A.toInt()         // OnSurfaceDark
        private const val ON_SURFACE_VARIANT = 0xFF6D6A64.toInt() // OnSurfaceVariantGrey
        private const val OUTLINE_COLOR = 0xFFCAC4D0.toInt()      // OutlineVariantLight

        private const val PAGE_WIDTH = 595f   // A4 @ 72dpi
        private const val PAGE_HEIGHT = 842f
        private const val MARGIN = 40f
        private const val FOOTER_HEIGHT = 28f
        private const val LINE = 22f
        private const val SECTION_GAP = 28f
        private const val BRAND_LABEL_SIZE = 9f
        private const val TITLE_SIZE = 28f
        private const val HEADER_SIZE = 16f
        private const val BODY_SIZE = 12f
        private const val CAPTION_SIZE = 10f

        // Fixed column X positions for detail rows
        private const val COL_TYPE = 190f
        private const val COL_DURATION = 310f

        // Fixed column X positions for summary rows
        private const val COL_SUMMARY_COUNT = 155f
        private const val COL_SUMMARY_AVG = 230f

        /** Pure pagination helper: pages needed for [rowCount] rows at [rowsPerPage]. Always >= 1. */
        fun paginate(rowCount: Int, rowsPerPage: Int): Int =
            if (rowCount <= 0) 1 else (rowCount + rowsPerPage - 1) / rowsPerPage
    }
}
