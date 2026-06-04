package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepEvalHarnessTest {

    private val zone = ZoneOffset.UTC
    private val baseNow = Instant.parse("2024-06-15T14:00:00Z")
    private val harness = SleepEvalHarness(zone)

    // Baby born 20 weeks before baseNow. Age = 20 weeks → ageBand = 16.
    private val baby = Baby(
        name = "TestBaby",
        birthDate = LocalDate.ofInstant(baseNow, zone).minusWeeks(20),
    )

    /**
     * Creates N completed NAP records with a stable 90-min wake window and 90-min nap duration.
     * Cycle = 180 min. Records run backwards from baseNow - 60 min.
     *
     * Record i (0 = newest, N-1 = oldest):
     *   end   = baseNow - 60min - i*180min
     *   start = end - 90min
     *
     * Wake interval between record[i] and record[i+1]: 90 min (constant, IQR ≈ 0).
     * N=32 records span ~95 h (~4 days) → LOCAL_DAYS ≥ 3 ✓ LOOKBACK_DAYS=14 ✓
     */
    private fun stableNapRecords(count: Int = 32): List<SleepRecord> {
        var id = 1L
        return (0 until count).map { i ->
            val napEnd = baseNow.minus(Duration.ofMinutes(60 + i * 180L))
            val napStart = napEnd.minus(Duration.ofMinutes(90))
            SleepRecord(id++, napStart, napEnd, SleepType.NAP)
        }.sortedBy { it.startTime }
    }

    @Nested
    inner class BlockInsufficientData {

        @Test
        fun `fewer than EVAL_MIN_ANCHORS records results in BLOCK`() {
            // 14 records → 13 wake anchors < 20 → BLOCK
            val records = stableNapRecords(14)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty())
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
        }

        @Test
        fun `BLOCK result has null metrics`() {
            val records = stableNapRecords(14)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val seg = report.segments.first()
            assertNull(seg.maeMinutes)
            assertNull(seg.inWindowPct)
            assertNull(seg.missedWindowRate)
        }

        @Test
        fun `BLOCK result contains human-readable blockReason`() {
            val records = stableNapRecords(14)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val seg = report.segments.first()
            assertNotNull(seg.blockReason)
            assertTrue(seg.blockReason!!.isNotBlank())
        }
    }

    @Nested
    inner class PassSegment {

        @Test
        fun `32 stable records produces a PASS segment with metrics`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.firstOrNull { it.status == SegmentStatus.PASS }
            assertNotNull(passSeg, "Expected at least one PASS segment")
            assertNotNull(passSeg!!.maeMinutes)
            assertNotNull(passSeg.inWindowPct)
            assertNotNull(passSeg.missedWindowRate)
        }

        @Test
        fun `PASS segment anchor count is at least EVAL_MIN_ANCHORS`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            assertTrue(passSeg.anchorCount >= SleepPredictionTuning.EVAL_MIN_ANCHORS)
        }

        @Test
        fun `PASS segment has positive scoredCount`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            assertTrue(passSeg.scoredCount > 0)
        }

        @Test
        fun `EvalReport carries correct algorithmVersion and evaluatedAt`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertEquals(SleepPredictionTuning.ALGORITHM_VERSION, report.algorithmVersion)
            assertEquals(baseNow, report.evaluatedAt)
        }

        @Test
        fun `inWindowPct is between 0 and 1`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            val pct = passSeg.inWindowPct!!
            assertTrue(pct in 0.0..1.0, "inWindowPct=$pct out of [0,1]")
        }

        @Test
        fun `missedWindowRate is between 0 and 1`() {
            val records = stableNapRecords(32)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            val rate = passSeg.missedWindowRate!!
            assertTrue(rate in 0.0..1.0, "missedWindowRate=$rate out of [0,1]")
        }
    }

    @Nested
    inner class NoLookahead {

        @Test
        fun `changing ground-truth record changes MAE but not scored count`() {
            val records = stableNapRecords(32)
            // Shift last record 3 hours earlier — changes ground truth for anchor[last-1]
            // but must NOT affect any prediction (predictions use only prior records)
            val shifted = records.dropLast(1) + records.last().copy(
                startTime = records.last().startTime.minus(Duration.ofHours(3)),
                endTime = records.last().endTime!!.minus(Duration.ofHours(3)),
            )
            val reportOriginal = harness.evaluate(records, emptyList(), baby, baseNow)
            val reportShifted = harness.evaluate(shifted, emptyList(), baby, baseNow)

            assertEquals(reportOriginal.totalAnchors, reportShifted.totalAnchors)
            assertEquals(reportOriginal.totalScored, reportShifted.totalScored)
            val origSeg = reportOriginal.segments.firstOrNull { it.status == SegmentStatus.PASS }
            val shiftSeg = reportShifted.segments.firstOrNull { it.status == SegmentStatus.PASS }
            if (origSeg != null && shiftSeg != null) {
                assertNotEquals(origSeg.maeMinutes, shiftSeg.maeMinutes,
                    "MAE should differ when ground truth changes")
            }
        }

        @Test
        fun `anchor 15 bestEstimate equals direct prior-only SleepWindowPredictor call`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val anchorIdx = 15

            // Direct prior-only computation — same logic the harness must use
            val wakeInstant = sorted[anchorIdx].endTime!!
            val priorRecords = sorted.subList(0, anchorIdx + 1)
            val lookbackStart = wakeInstant.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
            val clockAtWake = java.time.Clock.fixed(wakeInstant, zone)
            val features = com.babytracker.domain.sleep.feature.SleepFeatureExtractor(clockAtWake, zone)
                .extract(priorRecords.filter { it.startTime >= lookbackStart }, emptyList())
            val ageInWeeks = java.time.temporal.ChronoUnit.WEEKS.between(
                baby.birthDate, wakeInstant.atZone(zone).toLocalDate()
            ).toInt()
            val expectedState = SleepWindowPredictor.predict(features, ageInWeeks, wakeInstant)

            // Harness per-anchor result via internal buildAnchors
            val anchors = harness.buildAnchors(sorted, emptyList(), baby)
            val anchor = anchors.first { it.wakeInstant == wakeInstant }

            assertEquals(expectedState, anchor.predictedState,
                "Harness prediction at anchor $anchorIdx must exactly match direct prior-only call")
        }

        @Test
        fun `extreme mutation of future records does not change prior anchor predictions`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val cutoff = 15 // verify anchors 0..cutoff-1 are unaffected

            // Shift all records after cutoff by +100 h — they are only ever ground truth
            // for anchors at cutoff..N, never training data for anchors 0..cutoff-1
            val mutated = sorted.mapIndexed { i, r ->
                if (i > cutoff) r.copy(
                    startTime = r.startTime.plus(Duration.ofHours(100)),
                    endTime = r.endTime!!.plus(Duration.ofHours(100)),
                ) else r
            }

            val anchorsOriginal = harness.buildAnchors(sorted, emptyList(), baby)
            val anchorsMutated = harness.buildAnchors(mutated, emptyList(), baby)

            for (i in 0 until cutoff) {
                val orig = anchorsOriginal.getOrNull(i) ?: continue
                val mut = anchorsMutated.getOrNull(i) ?: continue
                assertEquals(orig.wakeInstant, mut.wakeInstant)
                assertEquals(orig.predictedState, mut.predictedState,
                    "Anchor $i prediction changed after mutating future records — lookahead detected")
            }
        }
    }

    @Nested
    inner class FeedPointInTime {

        @Test
        fun `feed crossing wake anchor is seen as active at that anchor`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val anchorIdx = 20
            val wakeInstant = sorted[anchorIdx].endTime!!

            // Feed started before wake, ends after wake → at wakeInstant it is active
            val crossingFeed = com.babytracker.domain.model.BreastfeedingSession(
                id = 1L,
                startTime = wakeInstant.minus(Duration.ofMinutes(30)),
                endTime = wakeInstant.plus(Duration.ofMinutes(30)),
                startingSide = com.babytracker.domain.model.BreastSide.LEFT,
            )

            val anchors = harness.buildAnchors(sorted, listOf(crossingFeed), baby)
            val anchor = anchors.first { it.wakeInstant == wakeInstant }

            assertInstanceOf(SleepPredictionState.AfterActiveFeed::class.java, anchor.predictedState,
                "Feed crossing wake anchor must appear active → AfterActiveFeed, not Window")
            assertNull(anchor.score, "AfterActiveFeed anchor must not be scored")
        }

        @Test
        fun `completed feed entirely before wake anchor is included normally`() {
            val records = stableNapRecords(32)
            val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
            val anchorIdx = 20
            val wakeInstant = sorted[anchorIdx].endTime!!

            // Feed entirely before wakeInstant — should not trigger AfterActiveFeed
            val completedFeed = com.babytracker.domain.model.BreastfeedingSession(
                id = 2L,
                startTime = wakeInstant.minus(Duration.ofHours(2)),
                endTime = wakeInstant.minus(Duration.ofHours(1)),
                startingSide = com.babytracker.domain.model.BreastSide.RIGHT,
            )

            val anchorsWithFeed = harness.buildAnchors(sorted, listOf(completedFeed), baby)
            val anchorsNoFeed = harness.buildAnchors(sorted, emptyList(), baby)
            val anchorWith = anchorsWithFeed.first { it.wakeInstant == wakeInstant }
            val anchorNo = anchorsNoFeed.first { it.wakeInstant == wakeInstant }

            // Completed past feed must not change the prediction type
            assertEquals(anchorNo.predictedState::class, anchorWith.predictedState::class,
                "Completed pre-wake feed should not change prediction type")
        }
    }
}
