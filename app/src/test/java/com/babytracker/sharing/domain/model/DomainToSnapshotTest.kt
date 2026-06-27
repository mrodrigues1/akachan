package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepReason
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
                reasons = listOf(SleepReason.Disruption, SleepReason.CircadianSlot),
                feedDue = true,
            ),
        )

        val snapshot = state.toSnapshot(
            generatedAt = generatedAt,
            resolveReason = { it::class.simpleName.orEmpty() },
            feedPromptText = "A breastfeed may be due near this window.",
        )

        assertEquals("WINDOW", snapshot?.stateLabel)
        assertEquals(1_000L, snapshot?.windowStart)
        assertEquals(2_000L, snapshot?.windowEnd)
        assertEquals(1_500L, snapshot?.bestEstimate)
        assertEquals("MEDIUM", snapshot?.confidence)
        assertEquals(listOf("Disruption", "CircadianSlot"), snapshot?.reasons)
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

        val snapshot = state.toSnapshot(generatedAt, { it.toString() }, "")

        assertEquals("NEED_MORE_DATA", snapshot?.stateLabel)
        assertNull(snapshot?.windowStart)
        assertNull(snapshot?.bestEstimate)
        assertNull(snapshot?.confidence)
        assertEquals(generatedAt, snapshot?.generatedAt)
    }

    @Test
    fun `CueLed maps label`() {
        assertEquals("CUE_LED", SleepPredictionState.CueLed.toSnapshot(generatedAt, { it.toString() }, "")?.stateLabel)
    }

    @Test
    fun `CurrentlySleeping maps label`() {
        assertEquals(
            "CURRENTLY_SLEEPING",
            SleepPredictionState.CurrentlySleeping.toSnapshot(generatedAt, { it.toString() }, "")?.stateLabel,
        )
    }

    @Test
    fun `AfterActiveFeed maps label`() {
        assertEquals(
            "AFTER_ACTIVE_FEED",
            SleepPredictionState.AfterActiveFeed.toSnapshot(generatedAt, { it.toString() }, "")?.stateLabel,
        )
    }

    @Test
    fun `Overdue maps label`() {
        assertEquals("OVERDUE", SleepPredictionState.Overdue.toSnapshot(generatedAt, { it.toString() }, "")?.stateLabel)
    }

    @Test
    fun `Unavailable returns null`() {
        assertNull(SleepPredictionState.Unavailable("no baby profile").toSnapshot(generatedAt, { it.toString() }, ""))
    }

    @Test
    fun `bottle feed snapshot carries clientId author and notes`() {
        val feed = BottleFeed(
            id = 1,
            clientId = "client-1",
            timestamp = Instant.ofEpochMilli(1000),
            volumeMl = 120,
            type = FeedType.BREAST_MILK,
            notes = "after nap",
            createdAt = Instant.ofEpochMilli(900),
            author = FeedAuthor.PARTNER,
        )

        val snapshot = feed.toSnapshot()

        assertEquals("client-1", snapshot.clientId)
        assertEquals("PARTNER", snapshot.author)
        assertEquals("after nap", snapshot.notes)
    }

    @Test
    fun `milk bag snapshot maps id date volume and notes`() {
        val bag = MilkBag(
            id = 7,
            collectionDate = Instant.ofEpochMilli(5000),
            volumeMl = 150,
            notes = "freezer",
            createdAt = Instant.ofEpochMilli(4000),
        )

        val snapshot = bag.toSnapshot()

        assertEquals(MilkBagSnapshot(id = 7, collectionDateMs = 5000, volumeMl = 150, notes = "freezer"), snapshot)
    }
}
