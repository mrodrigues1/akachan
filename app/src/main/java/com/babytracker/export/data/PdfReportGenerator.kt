package com.babytracker.export.data

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.util.formatDateTime
import com.babytracker.util.formatDuration
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportGenerator @Inject constructor() : PdfReportRenderer {

    override fun render(data: PdfReportData): ByteArray {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(newPageInfo(pageNumber))
        var canvas = page.canvas
        var y = MARGIN + TITLE_SIZE

        canvas.drawText("Baby Tracker — Health Summary", MARGIN, y, titlePaint)
        y += LINE
        canvas.drawText(
            "${data.range.start.formatDateTime()}  to  ${data.range.end.formatDateTime()}",
            MARGIN, y, captionPaint,
        )
        y += SECTION_GAP

        val feedingPos = ensureRoom(doc, page, y)
        page = feedingPos.page
        canvas = feedingPos.canvas
        y = feedingPos.y
        canvas.drawText("Feeding (${data.breastfeeding.size})", MARGIN, y, feedingHeaderPaint)
        y += LINE
        for (s in data.breastfeeding) {
            val pos = ensureRoom(doc, page, y)
            page = pos.page
            canvas = pos.canvas
            y = pos.y
            canvas.drawText(feedingRow(s), MARGIN, y, bodyPaint)
            y += LINE
        }
        y += SECTION_GAP

        val sleepPos = ensureRoom(doc, page, y)
        page = sleepPos.page
        canvas = sleepPos.canvas
        y = sleepPos.y
        canvas.drawText("Sleep (${data.sleep.size})", MARGIN, y, sleepHeaderPaint)
        y += LINE
        for (r in data.sleep) {
            val pos = ensureRoom(doc, page, y)
            page = pos.page
            canvas = pos.canvas
            y = pos.y
            canvas.drawText(sleepRow(r), MARGIN, y, bodyPaint)
            y += LINE
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private data class PagePos(
        val page: PdfDocument.Page,
        val canvas: android.graphics.Canvas,
        val y: Float,
    )

    private fun ensureRoom(doc: PdfDocument, current: PdfDocument.Page, y: Float): PagePos {
        if (y <= PAGE_HEIGHT - MARGIN) return PagePos(current, current.canvas, y)
        val next = current.info.pageNumber + 1
        doc.finishPage(current)
        val page = doc.startPage(newPageInfo(next))
        return PagePos(page, page.canvas, MARGIN + LINE)
    }

    private fun newPageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create()

    private fun feedingRow(s: BreastfeedingSession): String {
        val duration = s.activeDuration?.formatDuration() ?: "—"
        return "${s.startTime.formatDateTime()}   ${s.startingSide.name.lowercase()}   $duration"
    }

    private fun sleepRow(r: SleepRecord): String {
        val duration = r.duration?.formatDuration() ?: "—"
        return "${r.startTime.formatDateTime()}   ${r.sleepType.name.lowercase()}   $duration"
    }

    private val titlePaint = Paint().apply {
        color = ON_SURFACE
        textSize = TITLE_SIZE
        isFakeBoldText = true
    }
    private val captionPaint = Paint().apply {
        color = ON_SURFACE_VARIANT
        textSize = CAPTION_SIZE
    }
    private val feedingHeaderPaint = Paint().apply {
        color = FEEDING
        textSize = HEADER_SIZE
        isFakeBoldText = true
    }
    private val sleepHeaderPaint = Paint().apply {
        color = SLEEP
        textSize = HEADER_SIZE
        isFakeBoldText = true
    }
    private val bodyPaint = Paint().apply {
        color = ON_SURFACE
        textSize = BODY_SIZE
    }

    companion object {
        // Design tokens re-declared as android.graphics.Color ints (cannot import Compose Color).
        private const val FEEDING = 0xFFC2185B.toInt()            // Pink700
        private const val SLEEP = 0xFF1976D2.toInt()              // Blue700
        private const val ON_SURFACE = 0xFF1A1A1A.toInt()         // OnSurfaceDark
        private const val ON_SURFACE_VARIANT = 0xFF6D6A64.toInt() // OnSurfaceVariantGrey

        private const val PAGE_WIDTH = 595f   // A4 @ 72dpi
        private const val PAGE_HEIGHT = 842f
        private const val MARGIN = 40f
        private const val LINE = 22f
        private const val SECTION_GAP = 28f
        private const val TITLE_SIZE = 28f
        private const val HEADER_SIZE = 18f
        private const val BODY_SIZE = 14f
        private const val CAPTION_SIZE = 12f

        /** Pure pagination helper: pages needed for [rowCount] rows at [rowsPerPage]. Always >= 1. */
        fun paginate(rowCount: Int, rowsPerPage: Int): Int =
            if (rowCount <= 0) 1 else (rowCount + rowsPerPage - 1) / rowsPerPage
    }
}
