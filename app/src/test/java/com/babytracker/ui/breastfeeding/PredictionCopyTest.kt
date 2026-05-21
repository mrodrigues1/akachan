package com.babytracker.ui.breastfeeding

import com.babytracker.domain.model.FeedPrediction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictionCopyTest {

    private val anchor = Instant.parse("2026-05-19T17:40:00Z")

    @Test
    fun `future prediction beyond 30 minutes uses single line`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 45)
        )
        assertTrue(subtitle.primary.startsWith("Likely hungry around "))
        assertNull(subtitle.secondary)
    }

    @Test
    fun `future prediction within 30 minutes shows secondary in-X-m`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 12)
        )
        assertEquals("in ~12m", subtitle.secondary)
    }

    @Test
    fun `overdue 5 minutes or more shows hungry now copy`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 4, isOverdue = true, minutesUntil = -7)
        )
        assertTrue(subtitle.primary.contains("hungry now"))
        assertTrue(subtitle.primary.contains("~7m ago"))
    }

    @Test
    fun `overdue less than 5 minutes shows likely hungry around copy`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 4, isOverdue = true, minutesUntil = -3)
        )
        assertTrue(subtitle.primary.startsWith("Likely hungry around "))
        assertNull(subtitle.secondary)
    }

    @Test
    fun `future prediction at exactly 30 minutes shows secondary`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 30)
        )
        assertEquals("in ~30m", subtitle.secondary)
    }

    @Test
    fun `sample size 3 marks low confidence`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 3, isOverdue = false, minutesUntil = 50)
        )
        assertTrue(subtitle.lowConfidence)
    }

    @Test
    fun `sample size 5 is not low confidence`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 50)
        )
        assertEquals(false, subtitle.lowConfidence)
    }

    @Test
    fun `content description includes basis for all prediction types`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 45)
        )
        assertTrue(subtitle.contentDescription.contains("based on 5 recent feeds"))
    }

    @Test
    fun `content description includes low confidence when sample size is 3`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 3, isOverdue = false, minutesUntil = 45)
        )
        assertTrue(subtitle.contentDescription.contains("low confidence"))
    }
}
