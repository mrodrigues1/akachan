package com.babytracker.ui.breastfeeding

import com.babytracker.domain.model.FeedPrediction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PredictionCopyTest {

    private val anchor = Instant.parse("2026-05-19T17:40:00Z")

    @Test
    fun `future prediction beyond 30 minutes is upcoming without detail`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 45)
        )
        assertEquals(PredictionCopy.Kind.UPCOMING, subtitle.kind)
    }

    @Test
    fun `future prediction within 30 minutes carries minutes-until detail`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 12)
        )
        assertEquals(PredictionCopy.Kind.UPCOMING_DETAIL, subtitle.kind)
        assertEquals(12, subtitle.minutesUntil)
    }

    @Test
    fun `overdue 5 minutes or more is hungry-now with ago minutes`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 4, isOverdue = true, minutesUntil = -7)
        )
        assertEquals(PredictionCopy.Kind.OVERDUE_NOW, subtitle.kind)
        assertEquals(7, subtitle.agoMinutes)
    }

    @Test
    fun `overdue less than 5 minutes falls back to upcoming`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 4, isOverdue = true, minutesUntil = -3)
        )
        assertEquals(PredictionCopy.Kind.UPCOMING, subtitle.kind)
    }

    @Test
    fun `future prediction at exactly 30 minutes carries detail`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 30)
        )
        assertEquals(PredictionCopy.Kind.UPCOMING_DETAIL, subtitle.kind)
        assertEquals(30, subtitle.minutesUntil)
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
    fun `subtitle carries sample size for the basis description`() {
        val subtitle = PredictionCopy.forPrediction(
            FeedPrediction(anchor, 180, sampleSize = 5, isOverdue = false, minutesUntil = 45)
        )
        assertEquals(5, subtitle.sampleSize)
    }
}
