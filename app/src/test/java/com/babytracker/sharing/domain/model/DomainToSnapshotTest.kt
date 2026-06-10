package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainToSnapshotTest {

    private val generatedAt = 1_700_000_000_000L

    @Test
    fun `Window maps all fields`() {
        val state = SleepPredictionState.Window(
            SleepWindow(
                windowStart = Instant.ofEpochMilli(1_000L),
                windowEnd = Instant.ofEpochMilli(2_000L),
                bestEstimate = Instant.ofEpochMilli(1_500L),
                confidence = Confidence.MEDIUM,
                reasons = listOf("Awake 2h", "Recent wake patterns"),
                feedPrompt = "A breastfeed may be due near this window.",
                safetyPrompt = "Back to sleep",
            ),
        )

        val snapshot = state.toSnapshot(generatedAt)

        assertEquals("WINDOW", snapshot?.stateLabel)
        assertEquals(1_000L, snapshot?.windowStart)
        assertEquals(2_000L, snapshot?.windowEnd)
        assertEquals(1_500L, snapshot?.bestEstimate)
        assertEquals("MEDIUM", snapshot?.confidence)
        assertEquals(listOf("Awake 2h", "Recent wake patterns"), snapshot?.reasons)
        assertEquals("A breastfeed may be due near this window.", snapshot?.feedPrompt)
        assertEquals(generatedAt, snapshot?.generatedAt)
    }

    @Test
    fun `NeedMoreData maps label and leaves window fields null`() {
        val state = SleepPredictionState.NeedMoreData(
            EvidenceProgress(
                completedIntervals = 2,
                requiredIntervals = 7,
                localDays = 1,
                requiredLocalDays = 3,
                hint = "Keep logging",
            ),
        )

        val snapshot = state.toSnapshot(generatedAt)

        assertEquals("NEED_MORE_DATA", snapshot?.stateLabel)
        assertNull(snapshot?.windowStart)
        assertNull(snapshot?.bestEstimate)
        assertNull(snapshot?.confidence)
        assertEquals(generatedAt, snapshot?.generatedAt)
    }

    @Test
    fun `CueLed maps label`() {
        assertEquals("CUE_LED", SleepPredictionState.CueLed.toSnapshot(generatedAt)?.stateLabel)
    }

    @Test
    fun `CurrentlySleeping maps label`() {
        assertEquals(
            "CURRENTLY_SLEEPING",
            SleepPredictionState.CurrentlySleeping.toSnapshot(generatedAt)?.stateLabel,
        )
    }

    @Test
    fun `AfterActiveFeed maps label`() {
        assertEquals(
            "AFTER_ACTIVE_FEED",
            SleepPredictionState.AfterActiveFeed.toSnapshot(generatedAt)?.stateLabel,
        )
    }

    @Test
    fun `Overdue maps label`() {
        assertEquals("OVERDUE", SleepPredictionState.Overdue.toSnapshot(generatedAt)?.stateLabel)
    }

    @Test
    fun `Unavailable returns null`() {
        assertNull(SleepPredictionState.Unavailable("no baby profile").toSnapshot(generatedAt))
    }
}
