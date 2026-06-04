package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepFeatures
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
    private fun stableNapRecords(count: Int = 60): List<SleepRecord> {
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

        @Test
        fun `BLOCK when anchor count sufficient but zero Window predictions due to filtered wake intervals`() {
            // 25 NIGHT_SLEEP records, 1 per day with 16h wake intervals.
            // 24 wake anchors >= EVAL_MIN_ANCHORS, but 16h > MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS (6h)
            // → all wake intervals filtered → NeedMoreData for all anchors → 0 scored → BLOCK
            var id = 1L
            val records = (0 until 25).map { i ->
                val sleepEnd = baseNow.minus(Duration.ofHours((25 - i) * 24L - 6))
                val sleepStart = sleepEnd.minus(Duration.ofHours(8))
                SleepRecord(id++, sleepStart, sleepEnd, SleepType.NIGHT_SLEEP)
            }
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
                "0 scored with sufficient anchors must BLOCK; got: ${report.segments}")
            val nightSeg = report.segments.firstOrNull { it.key.sleepType == SleepType.NIGHT_SLEEP }
            assertNotNull(nightSeg)
            assertNotNull(nightSeg!!.blockReason)
            assertTrue(nightSeg.blockReason!!.contains("scored"), "blockReason must mention scored anchors: ${nightSeg.blockReason}")
        }

        @Test
        fun `PASS segment scoredCount is at least EVAL_MIN_SCORED`() {
            // Regression guard: EVAL_MIN_SCORED = 20 ensures the scored-anchor CI is statistically
            // meaningful (±22% at 95% confidence). A segment that scores exactly EVAL_MIN_SCORED - 1
            // anchors must BLOCK; a segment with >= EVAL_MIN_SCORED scored anchors must PASS.
            // 60 stable records → ~42 anchors, ~30+ Window-scored → PASS ✓
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.firstOrNull { it.status == SegmentStatus.PASS }
            assertNotNull(passSeg, "60 stable records must yield a PASS segment")
            assertTrue(
                passSeg!!.scoredCount >= SleepPredictionTuning.EVAL_MIN_SCORED,
                "PASS segment scoredCount=${passSeg.scoredCount} must be >= EVAL_MIN_SCORED (${SleepPredictionTuning.EVAL_MIN_SCORED})",
            )
        }

        @Test
        fun `EVAL_MIN_SCORED constant is 20 - boundary guard against silent regression`() {
            // Hard-coded sentinel: if EVAL_MIN_SCORED is ever reduced below 20, the statistical
            // power of the eval harness degrades to a meaningless CI width.
            // 95% CI half-width for n=20 proportions: ±1.96*sqrt(0.25/20) ≈ ±22% — acceptable floor.
            // n=5 (old value) gives ±44% — statistically meaningless.
            // This assertion must fail loudly if anyone reverts the threshold silently.
            assertEquals(20, SleepPredictionTuning.EVAL_MIN_SCORED,
                "EVAL_MIN_SCORED must be 20 for statistical validity; " +
                    "lower values produce CI > ±44% on inWindowPct. Update this test deliberately if the threshold changes.")
        }
    }

    @Nested
    inner class PassSegment {

        @Test
        fun `60 stable records produces a PASS segment with at least EVAL_MIN_SCORED Window predictions`() {
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.firstOrNull { it.status == SegmentStatus.PASS }
            assertNotNull(passSeg, "Expected at least one PASS segment")
            assertNotNull(passSeg!!.maeMinutes)
            assertNotNull(passSeg.inWindowPct)
            assertNotNull(passSeg.missedWindowRate)
        }

        @Test
        fun `PASS segment anchor count is at least EVAL_MIN_ANCHORS`() {
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            assertTrue(passSeg.anchorCount >= SleepPredictionTuning.EVAL_MIN_ANCHORS)
        }

        @Test
        fun `PASS segment has positive scoredCount`() {
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            assertTrue(passSeg.scoredCount > 0)
        }

        @Test
        fun `EvalReport carries correct algorithmVersion and evaluatedAt`() {
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertEquals(SleepPredictionTuning.ALGORITHM_VERSION, report.algorithmVersion)
            assertEquals(baseNow, report.evaluatedAt)
        }

        @Test
        fun `inWindowPct is between 0 and 1`() {
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            val passSeg = report.segments.first { it.status == SegmentStatus.PASS }
            val pct = passSeg.inWindowPct!!
            assertTrue(pct in 0.0..1.0, "inWindowPct=$pct out of [0,1]")
        }

        @Test
        fun `missedWindowRate is between 0 and 1`() {
            val records = stableNapRecords(60)
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
            val records = stableNapRecords(60)
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
            assertNotNull(origSeg, "expected PASS segment in original report")
            assertNotNull(shiftSeg, "expected PASS segment in shifted report")
            assertNotEquals(origSeg!!.maeMinutes, shiftSeg!!.maeMinutes,
                "MAE should differ when ground truth changes")
        }

        @Test
        fun `anchor 15 bestEstimate equals direct prior-only SleepWindowPredictor call`() {
            val records = stableNapRecords(60)
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
            val records = stableNapRecords(60)
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
            val records = stableNapRecords(60)
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
            val records = stableNapRecords(60)
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

    @Nested
    inner class SparseAndNoisyFixtures {

        /**
         * Sparse logger: one NAP per day for 15 days.
         * 14 potential anchors < EVAL_MIN_ANCHORS (20) → BLOCK.
         */
        @Test
        fun `sparse logger - one sleep per day produces BLOCK`() {
            var id = 1L
            val records = (0 until 15).map { dayAgo ->
                val napEnd = baseNow.minus(Duration.ofDays(dayAgo.toLong())).minus(Duration.ofHours(2))
                SleepRecord(id++, napEnd.minus(Duration.ofHours(1)), napEnd, SleepType.NAP)
            }
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty(),
                "Sparse logger must still produce segments, not empty report")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
                "Sparse logger (1/day, 15 days) should BLOCK; got: ${report.segments}")
            // 14 records → 14 anchors, but < EVAL_MIN_ANCHORS; 0 scored because all anchors BLOCK
            assertTrue(report.totalAnchors <= 14,
                "Sparse logger must have <= 14 anchors; got ${report.totalAnchors}")
            assertEquals(0, report.totalScored,
                "Sparse logger should score 0 anchors; got ${report.totalScored}")
            assertTrue(report.segments.all { it.scoredCount == 0 },
                "Each segment must have scoredCount == 0 in sparse logger")
        }

        /**
         * Nights-only: 25 NIGHT_SLEEP records with ~15 h wake windows between them.
         * Wake intervals far exceed MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS (6 h) → filtered out →
         * completedWakeIntervals = [] → NeedMoreData for all anchors → 0 scored → BLOCK.
         */
        @Test
        fun `nights-only - wake intervals exceed plausibility ceiling, all anchors return NeedMoreData`() {
            var id = 1L
            val records = (0 until 25).map { i ->
                // Night sleep: 10 pm to 6 am → 8 h sleep, 16 h wake window → > MAX (6 h)
                val sleepEnd = baseNow.minus(Duration.ofHours((25 - i) * 24L - 6))
                val sleepStart = sleepEnd.minus(Duration.ofHours(8))
                SleepRecord(id++, sleepStart, sleepEnd, SleepType.NIGHT_SLEEP)
            }
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty(),
                "Nights-only must still produce segments, not empty report")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
                "Nights-only with 16h wake windows should BLOCK; got: ${report.segments}")
            // 24 potential anchors are found but all return NeedMoreData → 0 scored
            assertTrue(report.totalAnchors >= 24,
                "Should have 24 anchors from 25 records; got ${report.totalAnchors}")
            assertEquals(0, report.totalScored,
                "All anchors return NeedMoreData → 0 scored; got ${report.totalScored}")
            val nightSeg = report.segments.firstOrNull { it.key.sleepType == SleepType.NIGHT_SLEEP }
            assertNotNull(nightSeg, "NIGHT_SLEEP segment must be present")
            assertEquals(0, nightSeg!!.scoredCount,
                "NIGHT_SLEEP segment scoredCount must be 0; got ${nightSeg.scoredCount}")
        }

        /**
         * Naps-only: 15 NAP records.
         * NAP segment: 14 anchors < 20 → BLOCK.
         * NIGHT_SLEEP segment: 0 anchors → BLOCK (segment grid materialization ensures it appears).
         */
        @Test
        fun `naps-only - both NAP and NIGHT_SLEEP segments appear as BLOCK`() {
            var id = 1L
            val records = (0 until 15).map { i ->
                val napEnd = baseNow.minus(Duration.ofMinutes(60 + i * 180L))
                SleepRecord(id++, napEnd.minus(Duration.ofMinutes(90)), napEnd, SleepType.NAP)
            }
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            // Both segment types must be present — not an empty report
            assertEquals(SleepType.entries.size, report.segments.size,
                "Expected one segment per SleepType; got ${report.segments.size}")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
            // NAP segment: up to 14 anchors, all below EVAL_MIN_ANCHORS threshold
            val napSeg = report.segments.firstOrNull { it.key.sleepType == SleepType.NAP }
            assertNotNull(napSeg, "NAP segment must be present")
            assertTrue(napSeg!!.anchorCount <= 14,
                "NAP segment anchorCount must be <= 14 for 15 records; got ${napSeg.anchorCount}")
            assertEquals(0, napSeg.scoredCount, "NAP segment scoredCount must be 0")
            // Segment grid materializes NIGHT_SLEEP with 0 anchors — it must be present as BLOCK,
            // not silently absent.
            val nightSeg = report.segments.firstOrNull { it.key.sleepType == SleepType.NIGHT_SLEEP }
            assertNotNull(nightSeg, "NIGHT_SLEEP segment must appear as BLOCK even with 0 anchors")
            assertEquals(0, nightSeg!!.anchorCount)
            assertEquals(0, nightSeg.scoredCount, "NIGHT_SLEEP segment scoredCount must be 0")
        }

        /**
         * Very erratic intervals: alternating 60 min and 4 h wake windows.
         * IQR = 180 min >> INSTABILITY_CEILING_MINUTES (45 min) AND record span < 3 local days →
         * hasSufficientZoneIndependentEvidence = false → NeedMoreData for all anchors → 0 scored → BLOCK.
         */
        @Test
        fun `erratic intervals - high IQR causes NeedMoreData for all anchors, segment BLOCKS`() {
            var id = 1L
            // Alternating short (60 min) and long (240 min) wake intervals between 90-min naps.
            // Build 25 naps from the end backwards.
            val records = mutableListOf<SleepRecord>()
            var cursor = baseNow.minus(Duration.ofMinutes(60)) // end of most recent nap
            repeat(25) { i ->
                val napEnd = cursor
                val napStart = napEnd.minus(Duration.ofMinutes(90))
                records += SleepRecord(id++, napStart, napEnd, SleepType.NAP)
                // Alternate wake gap: even iterations use 60 min, odd use 240 min
                val wakeGap = if (i % 2 == 0) 60L else 240L
                cursor = napStart.minus(Duration.ofMinutes(wakeGap))
            }
            val sorted = records.sortedBy { it.startTime }
            val report = harness.evaluate(sorted, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty(),
                "Erratic fixture must still produce segments, not empty report")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
                "Erratic (IQR >> 45 min) fixture should BLOCK; got: ${report.segments}")
            // Anchors exist (25 records → up to 24 potential) but all return NeedMoreData → 0 scored
            assertTrue(report.totalAnchors >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
                "Erratic fixture must have >= EVAL_MIN_ANCHORS (${SleepPredictionTuning.EVAL_MIN_ANCHORS}) anchors, got ${report.totalAnchors}")
            assertEquals(0, report.totalScored,
                "All anchors return NeedMoreData due to high IQR → 0 scored; got ${report.totalScored}")
            assertTrue(report.segments.all { it.scoredCount == 0 },
                "Each segment must have scoredCount == 0")
        }

        /**
         * Overlapping records: 25 naps all overlapping each other within a 2-hour window.
         * SleepFeatureExtractor.removeOverlapping() drops the entire cluster → 0 completed intervals →
         * NeedMoreData for all anchors → 0 scored → BLOCK.
         */
        @Test
        fun `overlapping records - all dropped by removeOverlapping, segment BLOCKS`() {
            var id = 1L
            // All records start within a 30-min band and end within another 30-min band → one giant cluster
            val clusterStart = baseNow.minus(Duration.ofHours(4))
            val records = (0 until 25).map { i ->
                val start = clusterStart.plus(Duration.ofMinutes(i.toLong()))
                val end = start.plus(Duration.ofHours(1))
                SleepRecord(id++, start, end, SleepType.NAP)
            }
            val report = harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty(),
                "Overlapping cluster must still produce segments, not empty report")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
                "Overlapping cluster fixture should BLOCK; got: ${report.segments}")
            // removeOverlapping drops the cluster → 0 scored anchors
            assertEquals(0, report.totalScored,
                "Overlapping cluster should score 0 anchors; got ${report.totalScored}")
            assertTrue(report.segments.all { it.scoredCount == 0 },
                "Each segment must have scoredCount == 0")
        }
    }

    @Nested
    inner class InjectedPredictor {

        @Test
        fun `custom predictor function is called instead of default`() {
            var callCount = 0
            val countingPredictor: (SleepFeatures, Int, Instant) -> SleepPredictionState = { features, age, now ->
                callCount++
                SleepWindowPredictor.predict(features, age, now)
            }
            val harness = SleepEvalHarness(zone, countingPredictor)
            val records = stableNapRecords(30)
            harness.evaluate(records, emptyList(), baby, baseNow)
            assertTrue(callCount > 0, "Custom predictor must be called at least once; callCount=$callCount")
        }
    }

    @Nested
    inner class ZeroAnchorEdgeCases {

        /**
         * Empty records: no sleep history at all.
         * Segment grid derives from baby's current age band → NAP + NIGHT_SLEEP both BLOCK.
         */
        @Test
        fun `empty records - report emits BLOCK segments for baby current age band`() {
            val report = harness.evaluate(emptyList(), emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty(),
                "Empty records must still produce segments (derived from baby age), not empty report")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
            assertEquals(0, report.totalAnchors)
            assertEquals(SleepType.entries.size, report.segments.size,
                "Expected one BLOCK segment per SleepType for baby's current age band")
        }

        /**
         * One completed record: valid but no nextRecord → 0 anchors.
         * Must still emit BLOCK segments, not an empty report.
         */
        @Test
        fun `single completed record - zero anchors emits BLOCK segments`() {
            val singleRecord = listOf(
                SleepRecord(
                    id = 1L,
                    startTime = baseNow.minus(Duration.ofHours(2)),
                    endTime = baseNow.minus(Duration.ofHours(1)),
                    sleepType = SleepType.NAP,
                )
            )
            val report = harness.evaluate(singleRecord, emptyList(), baby, baseNow)
            assertTrue(report.segments.isNotEmpty())
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
            assertEquals(0, report.totalAnchors)
            assertEquals(0, report.totalScored)
            assertTrue(report.segments.all { it.anchorCount == 0 && it.scoredCount == 0 })
        }

        /**
         * All records belong to a baby under CUE_LED_MAX_AGE_WEEKS — all anchors skipped.
         * Segment grid still derives from baby's current age band → BLOCK segments emitted.
         */
        @Test
        fun `all-cue-led records - skipped anchors still produce BLOCK segments`() {
            // Baby born 4 weeks ago → ageInWeeks = 4 < CUE_LED_MAX_AGE_WEEKS (6)
            val youngBaby = Baby(
                name = "YoungBaby",
                birthDate = LocalDate.ofInstant(baseNow, zone).minusWeeks(4),
            )
            val records = stableNapRecords(60)
            val report = harness.evaluate(records, emptyList(), youngBaby, baseNow)
            assertTrue(report.segments.isNotEmpty(),
                "Cue-led dataset must still emit segments, not empty report")
            assertTrue(report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA })
            assertEquals(0, report.totalAnchors)
            assertEquals(0, report.totalScored)
            assertTrue(report.segments.all { it.anchorCount == 0 && it.scoredCount == 0 },
                "All segments must have anchorCount == 0 and scoredCount == 0 for cue-led baby")
        }
    }
}
