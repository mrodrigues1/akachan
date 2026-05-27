package com.babytracker.export.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
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
        var y = MARGIN + TITLE_SIZE

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
        y += LINE * 0.2f

        for (s in data.breastfeeding) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                var hy = drawColumnHeaders(c, MARGIN + LINE, "Date & Time", "Side", "Duration")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.2f
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
        y += LINE * 0.2f

        for (r in data.sleep) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                var hy = drawColumnHeaders(c, MARGIN + LINE, "Date & Time", "Type", "Duration")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.2f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawSleepRow(canvas, y, r)
        }

        drawPageFooter(page.canvas, page.info.pageNumber, totalPages)
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawSummary(canvas: android.graphics.Canvas, startY: Float, data: PdfReportData): Float {
        val rangeDays = Duration.between(data.range.start, data.range.end).toDays().coerceAtLeast(1)
        val avgFeedPerDay = "%.1f".format(data.breastfeeding.size.toFloat() / rangeDays)
        val avgSleepPerDay = "%.1f".format(data.sleep.size.toFloat() / rangeDays)

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

        var y = MARGIN + TITLE_SIZE + LINE + SECTION_GAP
        y += LINE * 0.6f + LINE + LINE + LINE + LINE * 0.6f  // drawSummary block
        y += SECTION_GAP
        y += SECTION_GAP  // after separator

        // Feeding section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.2f

        repeat(data.breastfeeding.size) {
            y = sim(y, MARGIN + LINE * 2.2f)
            y += LINE
        }
        y += SECTION_GAP

        // Sleep section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.2f

        repeat(data.sleep.size) {
            y = sim(y, MARGIN + LINE * 2.2f)
            y += LINE
        }

        return pageCount
    }

    private fun newPageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create()

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
        private const val ON_SURFACE = 0xFF1A1A1A.toInt()         // OnSurfaceDark
        private const val ON_SURFACE_VARIANT = 0xFF6D6A64.toInt() // OnSurfaceVariantGrey
        private const val OUTLINE_COLOR = 0xFFCAC4D0.toInt()      // OutlineVariantLight

        private const val PAGE_WIDTH = 595f   // A4 @ 72dpi
        private const val PAGE_HEIGHT = 842f
        private const val MARGIN = 40f
        private const val FOOTER_HEIGHT = 28f
        private const val LINE = 22f
        private const val SECTION_GAP = 28f
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
