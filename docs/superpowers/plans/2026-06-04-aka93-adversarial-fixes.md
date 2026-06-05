# AKA-93 Adversarial Review Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two adversarial-review findings in the AKA-93 sleep prediction branch: (1) the combined-IQR quality gate blocks the new type-separated predictor on realistic mixed data, and (2) skipped-nap days misroute to the nap model even when bedtime is imminent.

**Architecture:** Finding 1 adds an OR-branch to `SleepFeatureExtractor.computeQuality()` — the gate passes when both type-specific IQRs are within the instability ceiling, even if combined IQR is not. Finding 2 threads `currentMinuteOfDay` (computed by the extractor) through `SleepFeatures` → `SleepWindowPredictor.resolveNextSleepType()`, where a learned-bedtime cutoff overrides the count-only routing.

**Tech Stack:** Kotlin, JUnit 5, domain layer only — no Android, no Room, no Hilt changes.

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt` | Add `currentMinuteOfDay: Int? = null` field |
| `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt` | Set `currentMinuteOfDay` in `extract()`; add `isTypeAwareStable()` helper; update `computeQuality()` instability check |
| `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt` | Add `currentMinuteOfDay: Int? = null` param to `resolveNextSleepType()`; add bedtime cutoff logic; update `buildWindow()` call |
| `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt` | Two new tests for the type-aware IQR gate |
| `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt` | Resolver + production-path tests for skipped-nap routing, including after-bedtime wrap-around |
| `app/src/test/java/com/babytracker/domain/sleep/eval/PersonalizedWakeEvalComparisonTest.kt` | New wide-IQR fixture + eval test |

---

## Task 1: Finding 1 — Write failing tests for type-aware IQR gate

**Files:**
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1: Add two failing tests at the end of SleepFeatureExtractorTest (before the closing `}`)**

  The first test verifies the happy path (both type IQRs stable, combined IQR wide → should pass but currently fails).
  The second verifies the guard (one type has < 4 intervals → type-aware check can't fire → should still fail).

  ```kotlin
  @Test
  fun `quality passes when combined IQR wide but both type-specific IQRs are stable`() {
      // 6 forward-built cycles: Night(8h) → 90m wake → Nap(1h) → 90m wake → Nap(1h) → 150m wake → Night...
      // This produces 12 nap wake intervals at 90 min and 6 bedtime wake intervals at 150 min.
      // Combined quartiles: P25=90, P75=150 → IQR=60 min > 45 min ceiling → old gate BLOCKS.
      // Nap IQR: 0 min ✓  Bedtime IQR: 0 min ✓ → type-aware gate should PASS after fix.
      val intervals = buildWideIqrMixedIntervals()
      val metrics = extractor.computeMetrics(intervals)
      val quality = extractor.computeQuality(intervals, intervals.size, metrics)

      assertTrue(
          metrics.wakeIntervalIqrMillis != null &&
              metrics.wakeIntervalIqrMillis!! > Duration.ofMinutes(45).toMillis(),
          "Fixture combined IQR must exceed 45-min ceiling; got ${metrics.wakeIntervalIqrMillis?.div(60_000)} min",
      )
      assertNotNull(metrics.napWakeP25Millis, "Need ≥ 4 nap intervals (have ${metrics.napWakeIntervalCount})")
      assertNotNull(metrics.bedtimeWakeP25Millis, "Need ≥ 4 bedtime intervals (have ${metrics.bedtimeWakeIntervalCount})")
      assertTrue(
          quality.hasSufficientZoneIndependentEvidence,
          "Quality must pass when both type-specific IQRs are stable even if combined IQR exceeds ceiling",
      )
  }

  @Test
  fun `quality fails when combined IQR wide and type-specific quartiles unavailable`() {
      // 3 cycles: Night(8h) → 60m wake → Nap(1h) → 180m wake → Night...
      // Produces 3 nap intervals (60 min) and 3 bedtime intervals (180 min).
      // Combined P25=60, P75=180 → IQR=120 > 45 min → combined gate fails.
      // napWakeIntervalCount=3 < 4 → napWakeP25=null → isTypeAwareStable returns false.
      val intervals = buildThinMixedIntervals()
      val metrics = extractor.computeMetrics(intervals)
      val quality = extractor.computeQuality(intervals, intervals.size, metrics)

      assertTrue(
          metrics.wakeIntervalIqrMillis != null &&
              metrics.wakeIntervalIqrMillis!! > Duration.ofMinutes(45).toMillis(),
          "Combined IQR must exceed ceiling; got ${metrics.wakeIntervalIqrMillis?.div(60_000)} min",
      )
      assertNull(metrics.napWakeP25Millis, "napWakeP25 must be null with only 3 nap intervals")
      assertFalse(
          quality.hasSufficientZoneIndependentEvidence,
          "Quality must fail when not enough per-type intervals for type-aware stability check",
      )
  }
  ```

  Add these two private helpers inside `SleepFeatureExtractorTest` (alongside the existing helpers at the bottom of the class):

  ```kotlin
  /**
   * 6 forward-built cycles: Night(8h) → 90m → Nap(1h) → 90m → Nap(1h) → 150m → Night...
   * Produces 12 nap intervals (90 min each) + 6 bedtime intervals (150 min each).
   * Combined IQR = 60 min > INSTABILITY_CEILING_MINUTES (45 min).
   * Each type IQR = 0 min ≤ ceiling → type-aware gate should pass.
   */
  private fun buildWideIqrMixedIntervals(): List<SleepInterval> {
      val result = mutableListOf<SleepInterval>()
      // Total span: 6 cycles × 930 min = 5580 min ≈ 3.9 days (within 14-day lookback)
      val cycleMs = (8 * 60 + 90 + 60 + 90 + 60 + 150) * 60_000L
      var cursor = nowInstant.toEpochMilli() - 6 * cycleMs - 30 * 60_000L
      repeat(6) {
          val nightEnd = cursor + 8 * 60 * 60_000L
          result += SleepInterval.from(cursor, nightEnd, SleepType.NIGHT_SLEEP)!!
          cursor = nightEnd
          val nap1Start = cursor + 90 * 60_000L
          val nap1End = nap1Start + 60 * 60_000L
          result += SleepInterval.from(nap1Start, nap1End, SleepType.NAP)!!
          cursor = nap1End
          val nap2Start = cursor + 90 * 60_000L
          val nap2End = nap2Start + 60 * 60_000L
          result += SleepInterval.from(nap2Start, nap2End, SleepType.NAP)!!
          cursor = nap2End + 150 * 60_000L
      }
      return result.filter { it.endMillis!! <= nowInstant.toEpochMilli() }.sortedBy { it.startMillis }
  }

  /**
   * 3 forward-built cycles: Night(8h) → 60m → Nap(1h) → 180m → Night...
   * Produces 3 nap intervals (60 min) + 3 bedtime intervals (180 min).
   * Combined IQR = 120 min > ceiling; napWakeP25 = null (< 4 intervals).
   */
  private fun buildThinMixedIntervals(): List<SleepInterval> {
      val result = mutableListOf<SleepInterval>()
      val cycleMs = (8 * 60 + 60 + 60 + 180) * 60_000L
      var cursor = nowInstant.toEpochMilli() - 3 * cycleMs - 30 * 60_000L
      repeat(3) {
          val nightEnd = cursor + 8 * 60 * 60_000L
          result += SleepInterval.from(cursor, nightEnd, SleepType.NIGHT_SLEEP)!!
          cursor = nightEnd
          val napStart = cursor + 60 * 60_000L
          val napEnd = napStart + 60 * 60_000L
          result += SleepInterval.from(napStart, napEnd, SleepType.NAP)!!
          cursor = napEnd + 180 * 60_000L
      }
      return result.filter { it.endMillis!! <= nowInstant.toEpochMilli() }.sortedBy { it.startMillis }
  }
  ```

- [ ] **Step 2: Verify these tests are present in the file, then run them to confirm they FAIL**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.quality passes when combined IQR wide but both type-specific IQRs are stable" --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.quality fails when combined IQR wide and type-specific quartiles unavailable"
  ```

  Expected: FAIL on the first test (`quality passes when combined IQR wide...`), PASS on the second.
  The second test already passes because the current code rejects combined-IQR > ceiling regardless.

---

## Task 2: Finding 1 — Implement type-aware IQR gate

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`

- [ ] **Step 1: Add `isTypeAwareStable` private helper to `SleepFeatureExtractor`**

  Add this method in the private section of `SleepFeatureExtractor` (after the `iqr` helper, before `companion object`):

  ```kotlin
  private fun isTypeAwareStable(metrics: SleepMetrics, ceilingMillis: Long): Boolean {
      val napIqr = if (metrics.napWakeP25Millis != null && metrics.napWakeP75Millis != null) {
          metrics.napWakeP75Millis - metrics.napWakeP25Millis
      } else {
          null
      }
      val bedtimeIqr = if (metrics.bedtimeWakeP25Millis != null && metrics.bedtimeWakeP75Millis != null) {
          metrics.bedtimeWakeP75Millis - metrics.bedtimeWakeP25Millis
      } else {
          null
      }
      return napIqr != null && bedtimeIqr != null && napIqr <= ceilingMillis && bedtimeIqr <= ceilingMillis
  }
  ```

- [ ] **Step 2: Update `computeQuality()` to use an OR-gate for the instability check**

  Find this block in `computeQuality()` (lines 114–118):

  ```kotlin
  val instabilityCeilingMillis = Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES).toMillis()
  val hasSufficientZoneIndependentEvidence = isFresh &&
      metrics.completedWakeIntervals.size >= SleepPredictionTuning.MIN_COMPLETED_INTERVALS &&
      (metrics.wakeIntervalIqrMillis == null || metrics.wakeIntervalIqrMillis <= instabilityCeilingMillis) &&
      invalidRecordRate < SleepPredictionTuning.MAX_INVALID_RATE
  ```

  Replace with:

  ```kotlin
  val instabilityCeilingMillis = Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES).toMillis()
  val isStable = (metrics.wakeIntervalIqrMillis == null || metrics.wakeIntervalIqrMillis <= instabilityCeilingMillis) ||
      isTypeAwareStable(metrics, instabilityCeilingMillis)
  val hasSufficientZoneIndependentEvidence = isFresh &&
      metrics.completedWakeIntervals.size >= SleepPredictionTuning.MIN_COMPLETED_INTERVALS &&
      isStable &&
      invalidRecordRate < SleepPredictionTuning.MAX_INVALID_RATE
  ```

- [ ] **Step 3: Run the Task 1 tests to confirm they now pass**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.quality passes when combined IQR wide but both type-specific IQRs are stable" --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.quality fails when combined IQR wide and type-specific quartiles unavailable"
  ```

  Expected: both PASS.

- [ ] **Step 4: Run full unit test suite to check for regressions**

  ```
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Run ktlint and detekt**

  ```
  ./gradlew ktlintFormat detekt
  ```

  Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 6: Commit**

  ```
  git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt \
          app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
  git commit -m "fix(sleep): replace combined-IQR gate with type-aware OR-gate in computeQuality [AKA-93]"
  ```

---

## Task 3: Finding 1 — Add wide-IQR eval fixture test

**Files:**
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/PersonalizedWakeEvalComparisonTest.kt`

- [ ] **Step 1: Add the `wideIqrMixedDayRecords` fixture helper inside `PersonalizedWakeEvalComparisonTest`**

  Add after `napOnlyRecords()`:

  ```kotlin
  /**
   * Wide-IQR fixture: nap wake intervals = 90 min, bedtime wake intervals = 150 min.
   *
   * Combined IQR = P75 − P25 = 150 − 90 = 60 min > INSTABILITY_CEILING_MINUTES (45 min).
   * Under the old combined-IQR gate every anchor returns NeedMoreData.
   * Under the type-aware OR-gate both type IQRs are 0 ≤ ceiling → gate passes.
   *
   * Cycle (870 min ≈ 14.5 h):
   *   Night: 8 h
   *   90 min wake → Nap 1: 1 h
   *   90 min wake → Nap 2: 1 h
   *   150 min wake → next Night
   *
   * typeAwareWakeIntervals labels:
   *   Night→Nap1 gap (90 min) → label = NAP  ✓
   *   Nap1→Nap2  gap (90 min) → label = NAP  ✓
   *   Nap2→Night gap (150 min) → label = NIGHT_SLEEP ✓
   */
  private fun wideIqrMixedDayRecords(days: Int = 42): List<SleepRecord> {
      var id = 1L
      val records = mutableListOf<SleepRecord>()
      val cycleMinutes = (8 * 60 + 90 + 60 + 90 + 60 + 150).toLong()
      var cursor = baseNow.minus(Duration.ofMinutes(cycleMinutes * days + 60))
      repeat(days) {
          val nightEnd = cursor.plus(Duration.ofHours(8))
          records += SleepRecord(id++, cursor, nightEnd, SleepType.NIGHT_SLEEP)
          val nap1Start = nightEnd.plus(Duration.ofMinutes(90))
          val nap1End = nap1Start.plus(Duration.ofMinutes(60))
          records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)
          val nap2Start = nap1End.plus(Duration.ofMinutes(90))
          val nap2End = nap2Start.plus(Duration.ofMinutes(60))
          records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)
          cursor = nap2End.plus(Duration.ofMinutes(150))
      }
      return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
  }
  ```

- [ ] **Step 2: Add the eval test**

  Add after `sparse fixture correctly BLOCKS` test:

  ```kotlin
  @Test
  fun `wide combined IQR fixture passes type-aware gate and produces Window anchors`() {
      // Verify the fixture has combined IQR > INSTABILITY_CEILING_MINUTES.
      // The type-aware OR-gate fix must allow the predictor through.
      val records = wideIqrMixedDayRecords(42)
      val sorted = records.sortedBy { it.startTime }
      val extractor = SleepFeatureExtractor(Clock.fixed(baseNow, zone), zone)
      val lookbackStart = baseNow.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
      val recent = sorted.filter { it.startTime >= lookbackStart }
      val features = extractor.extract(recent, emptyList())

      assertTrue(
          features.metrics.wakeIntervalIqrMillis != null &&
              features.metrics.wakeIntervalIqrMillis!! >
              Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES).toMillis(),
          "Fixture must have combined IQR > ${SleepPredictionTuning.INSTABILITY_CEILING_MINUTES} min; " +
              "got ${features.metrics.wakeIntervalIqrMillis?.div(60_000)} min",
      )
      assertTrue(
          features.quality.hasSufficientZoneIndependentEvidence,
          "Type-aware gate must pass when both type-specific IQRs are stable",
      )

      val newHarness = SleepEvalHarness(zone)
      val anchors = newHarness.buildAnchors(sorted, emptyList(), baby)
      val fixtureStart = sorted.first().startTime
      val scoredRangeStart = fixtureStart.plus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
      val windowCount = anchors.count { it.wakeInstant >= scoredRangeStart && it.score != null }

      assertTrue(
          windowCount >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
          "Wide-IQR fixture must produce >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} scored anchors " +
              "in scored range; got $windowCount. Type-aware gate fix is not working.",
      )
  }
  ```

- [ ] **Step 3: Run the new eval test**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.eval.PersonalizedWakeEvalComparisonTest.wide combined IQR fixture passes type-aware gate and produces Window anchors"
  ```

  Expected: PASS (type-aware fix from Task 2 already in place).

- [ ] **Step 4: Run the full unit test suite**

  ```
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Commit**

  ```
  git add app/src/test/java/com/babytracker/domain/sleep/eval/PersonalizedWakeEvalComparisonTest.kt
  git commit -m "test(sleep): add wide-IQR eval fixture to prove type-aware gate fix [AKA-93]"
  ```

---

## Task 4: Finding 2 — Thread currentMinuteOfDay through SleepFeatures

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`
- Modify (test): `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1: Write failing tests for `currentMinuteOfDay` in `extract()`**

  Add to `SleepFeatureExtractorTest`:

  ```kotlin
  @Test
  fun `extract sets currentMinuteOfDay from clock and zone`() {
      // Clock is fixed at 14:00:00 UTC → minute of day = 14 * 60 = 840
      val features = extractor.extract(emptyList(), emptyList())
      assertEquals(
          14 * 60,
          features.currentMinuteOfDay,
          "currentMinuteOfDay must be hour*60+minute from the injected clock",
      )
  }

  @Test
  fun `extract sets currentMinuteOfDay from configured non UTC zone`() {
      // 14:00 UTC is 23:00 in Asia/Tokyo on this date.
      val tokyoExtractor = SleepFeatureExtractor(clock, ZoneId.of("Asia/Tokyo"))
      val features = tokyoExtractor.extract(emptyList(), emptyList())
      assertEquals(
          23 * 60,
          features.currentMinuteOfDay,
          "currentMinuteOfDay must use the extractor zoneId, not UTC or the device default zone",
      )
  }
  ```

- [ ] **Step 2: Run the tests to confirm they fail (field doesn't exist yet)**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.extract sets currentMinuteOfDay from clock and zone" --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.extract sets currentMinuteOfDay from configured non UTC zone"
  ```

  Expected: compilation error or FAIL because `SleepFeatures` has no `currentMinuteOfDay` field.

- [ ] **Step 3: Add `currentMinuteOfDay: Int? = null` to `SleepFeatures`**

  Replace the entire content of `SleepFeatures.kt`:

  ```kotlin
  package com.babytracker.domain.sleep.feature

  data class SleepFeatures(
      val validIntervals: List<SleepInterval>,
      val feedIntervals: List<BreastfeedInterval>,
      val metrics: SleepMetrics,
      val quality: EvidenceQuality,
      val currentMinuteOfDay: Int? = null,
  )
  ```

  The default value means all existing construction sites (unit tests, `SleepEvalHarness`) continue to compile unchanged.

- [ ] **Step 4: Set `currentMinuteOfDay` in `SleepFeatureExtractor.extract()`**

  Find the `extract()` function in `SleepFeatureExtractor.kt`:

  ```kotlin
  fun extract(
      sleepRecords: List<SleepRecord>,
      feedSessions: List<BreastfeedingSession>,
  ): SleepFeatures {
      val validIntervals = buildSleepIntervals(sleepRecords)
      val feedIntervals = buildBreastfeedIntervals(feedSessions)
      val metrics = computeMetrics(validIntervals)
      val quality = computeQuality(validIntervals, sleepRecords.size, metrics)
      return SleepFeatures(validIntervals, feedIntervals, metrics, quality)
  }
  ```

  Replace with:

  ```kotlin
  fun extract(
      sleepRecords: List<SleepRecord>,
      feedSessions: List<BreastfeedingSession>,
  ): SleepFeatures {
      val validIntervals = buildSleepIntervals(sleepRecords)
      val feedIntervals = buildBreastfeedIntervals(feedSessions)
      val metrics = computeMetrics(validIntervals)
      val quality = computeQuality(validIntervals, sleepRecords.size, metrics)
      val localTime = clock.instant().atZone(zoneId).toLocalTime()
      val currentMinuteOfDay = localTime.hour * 60 + localTime.minute
      return SleepFeatures(validIntervals, feedIntervals, metrics, quality, currentMinuteOfDay)
  }
  ```

  No new imports needed — `atZone` is already used in `minuteOfDay()`.

- [ ] **Step 5: Run the failing tests to confirm they now pass**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.extract sets currentMinuteOfDay from clock and zone" --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.extract sets currentMinuteOfDay from configured non UTC zone"
  ```

  Expected: both PASS.

- [ ] **Step 6: Run full suite**

  ```
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7: Run ktlint and detekt**

  ```
  ./gradlew ktlintFormat detekt
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

  ```
  git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt \
          app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt \
          app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
  git commit -m "feat(sleep): thread currentMinuteOfDay through SleepFeatures for bedtime routing [AKA-93]"
  ```

---

## Task 5: Finding 2 — Write failing tests for skipped-nap routing

**Files:**
- Modify: `app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt`

**Background:** `ageInWeeks = 20` (already in the test class).
- `SleepAgePriors.getNapWakeWindowMidpoint(20)`: `getDefaultWakeWindows(20)` returns `[105, 135, 150]`; napWindows = `[105, 135]`; avg = 120 min → threshold = 120.
- `SleepAgePriors.getScheduledNapCount(20)` = 2 (expected naps per day).

- [ ] **Step 1: Update the local `features()` helper so tests can pass `currentMinuteOfDay`**

  Find the helper near the top of `SleepWindowPredictorTest`:

  ```kotlin
  private fun features(
      quality: EvidenceQuality = sufficientQuality(),
      metrics: SleepMetrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ),
      feedIntervals: List<BreastfeedInterval> = emptyList(),
  ) = SleepFeatures(
      validIntervals = emptyList(),
      feedIntervals = feedIntervals,
      metrics = metrics,
      quality = quality,
  )
  ```

  Replace it with:

  ```kotlin
  private fun features(
      quality: EvidenceQuality = sufficientQuality(),
      metrics: SleepMetrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ),
      feedIntervals: List<BreastfeedInterval> = emptyList(),
      currentMinuteOfDay: Int? = null,
  ) = SleepFeatures(
      validIntervals = emptyList(),
      feedIntervals = feedIntervals,
      metrics = metrics,
      quality = quality,
      currentMinuteOfDay = currentMinuteOfDay,
  )
  ```

- [ ] **Step 2: Add resolver tests for before-bedtime, after-bedtime, midnight wrap, far-from-bedtime, and missing-bedtime cases**

  ```kotlin
  @Test
  fun `resolveNextSleepType returns NIGHT_SLEEP for skipped nap before learned bedtime threshold`() {
      // Baby 20w: napWakeWindowMidpoint = 120 min threshold.
      // medianBedtimeMinuteOfDay = 1200 (20:00). currentMinuteOfDay = 1140 (19:00).
      // minutesUntilBedtime = 60 min <= 120 min -> NIGHT_SLEEP.
      val metrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ).copy(
          lastSleepType = SleepType.NAP,
          napCountToday = 1,                   // < expected 2 → count check says NAP
          medianBedtimeMinuteOfDay = 20 * 60,  // learned bedtime 20:00
      )
      assertEquals(
          SleepType.NIGHT_SLEEP,
          SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 19 * 60),
          "Skipped nap with time 60 min before learned bedtime (threshold 120 min) must route to NIGHT_SLEEP",
      )
  }

  @Test
  fun `resolveNextSleepType returns NIGHT_SLEEP for skipped nap just after learned bedtime`() {
      // medianBedtimeMinuteOfDay = 1200 (20:00). currentMinuteOfDay = 1230 (20:30).
      // minutesSinceBedtime = 30 min <= 120 min -> NIGHT_SLEEP.
      val metrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ).copy(
          lastSleepType = SleepType.NAP,
          napCountToday = 1,
          medianBedtimeMinuteOfDay = 20 * 60,
      )
      assertEquals(
          SleepType.NIGHT_SLEEP,
          SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 20 * 60 + 30),
          "Skipped nap 30 min after learned bedtime (threshold 120 min) must route to NIGHT_SLEEP",
      )
  }

  @Test
  fun `resolveNextSleepType returns NIGHT_SLEEP for skipped nap after bedtime across midnight`() {
      // medianBedtimeMinuteOfDay = 1410 (23:30). currentMinuteOfDay = 10 (00:10).
      // minutesSinceBedtime wraps across midnight to 40 min <= 120 min -> NIGHT_SLEEP.
      val metrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ).copy(
          lastSleepType = SleepType.NAP,
          napCountToday = 1,
          medianBedtimeMinuteOfDay = 23 * 60 + 30,
      )
      assertEquals(
          SleepType.NIGHT_SLEEP,
          SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 10),
          "Skipped nap 40 min after a 23:30 learned bedtime must route to NIGHT_SLEEP across midnight",
      )
  }

  @Test
  fun `resolveNextSleepType returns NAP for skipped nap when still far from bedtime`() {
      // currentMinuteOfDay = 900 (15:00). minutesUntilBedtime = 300 min > 120 min threshold -> NAP.
      val metrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ).copy(
          lastSleepType = SleepType.NAP,
          napCountToday = 1,
          medianBedtimeMinuteOfDay = 20 * 60,
      )
      assertEquals(
          SleepType.NAP,
          SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 15 * 60),
          "Skipped nap with 300 min until learned bedtime (threshold 120 min) must still route to NAP",
      )
  }

  @Test
  fun `resolveNextSleepType returns NAP for skipped nap when no learned bedtime available`() {
      // medianBedtimeMinuteOfDay = null -> late-day override inactive regardless of time.
      val metrics = sufficientMetrics(
          lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
      ).copy(
          lastSleepType = SleepType.NAP,
          napCountToday = 1,
          medianBedtimeMinuteOfDay = null,
      )
      assertEquals(
          SleepType.NAP,
          SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 20 * 60),
          "Bedtime override must not fire when medianBedtimeMinuteOfDay is null",
      )
  }
  ```

- [ ] **Step 3: Add a production-path test proving `predict()` threads `features.currentMinuteOfDay` into `buildWindow()`**

  This test fails if `resolveNextSleepType()` works in isolation but `buildWindow()` still calls it without `features.currentMinuteOfDay`.

  ```kotlin
  @Test
  fun `predict routes skipped nap to bedtime model using features currentMinuteOfDay`() {
      val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
      val bedtimeP50 = Duration.ofMinutes(150).toMillis()
      val metrics = sufficientMetrics(
          lastWakeMillis = lastWakeMillis,
          medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
          bedtimeWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
          bedtimeWakeP50Millis = bedtimeP50,
      ).copy(
          lastSleepType = SleepType.NAP,
          napCountToday = 1,                  // < expected 2, so count-only routing would choose NAP
          medianBedtimeMinuteOfDay = 15 * 60, // learned bedtime 15:00
      )
      val result = SleepWindowPredictor.predict(
          features(
              quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS),
              metrics = metrics,
              currentMinuteOfDay = 14 * 60, // within the 120-min bedtime threshold
          ),
          ageInWeeks,
          baseNow,
      )

      val window = (result as SleepPredictionState.Window).window
      val bedtimePriorMillis = Duration.ofMinutes(150).toMillis()
      val expectedTargetMillis = (0.4 * bedtimePriorMillis + 0.6 * bedtimeP50).toLong()
      val expectedBestEstimate = Instant.ofEpochMilli(lastWakeMillis + expectedTargetMillis)

      assertEquals(
          expectedBestEstimate,
          window.bestEstimate,
          "predict() must route through the bedtime model when currentMinuteOfDay is within learned bedtime cutoff",
      )
      assertTrue(
          window.reasons.any { it.contains("bedtime-specific wake patterns") },
          "Window reasons must show the bedtime-specific path; buildWindow likely did not pass features.currentMinuteOfDay",
      )
  }
  ```

- [ ] **Step 4: Run the tests to confirm they fail before implementation**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NIGHT_SLEEP for skipped nap before learned bedtime threshold" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NIGHT_SLEEP for skipped nap just after learned bedtime" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NIGHT_SLEEP for skipped nap after bedtime across midnight" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NAP for skipped nap when still far from bedtime" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NAP for skipped nap when no learned bedtime available" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.predict routes skipped nap to bedtime model using features currentMinuteOfDay"
  ```

  Expected before Task 6: compilation fails because `resolveNextSleepType()` does not accept `currentMinuteOfDay`, or the NIGHT_SLEEP/prod-path tests fail with `NAP` routing.

---

## Task 6: Finding 2 — Implement skipped-nap routing fix

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt`

- [ ] **Step 1: Update `resolveNextSleepType` to accept `currentMinuteOfDay` and apply the bedtime cutoff**

  Find the current implementation:

  ```kotlin
  internal fun resolveNextSleepType(metrics: SleepMetrics, ageInWeeks: Int): SleepType =
      when (metrics.lastSleepType) {
          SleepType.NIGHT_SLEEP -> SleepType.NAP
          SleepType.NAP -> {
              val expected = SleepAgePriors.getScheduledNapCount(ageInWeeks)
              if (metrics.napCountToday < expected) SleepType.NAP else SleepType.NIGHT_SLEEP
          }
          null -> SleepType.NAP
      }
  ```

  Replace with:

  ```kotlin
  internal fun resolveNextSleepType(
      metrics: SleepMetrics,
      ageInWeeks: Int,
      currentMinuteOfDay: Int? = null,
  ): SleepType = when (metrics.lastSleepType) {
      SleepType.NIGHT_SLEEP -> SleepType.NAP
      SleepType.NAP -> {
          val expected = SleepAgePriors.getScheduledNapCount(ageInWeeks)
          if (metrics.napCountToday >= expected) {
              SleepType.NIGHT_SLEEP
          } else if (isWithinLearnedBedtimeCutoff(currentMinuteOfDay, metrics.medianBedtimeMinuteOfDay, ageInWeeks)) {
              SleepType.NIGHT_SLEEP
          } else {
              SleepType.NAP
          }
      }
      null -> SleepType.NAP
  }
  ```

- [ ] **Step 2: Add the learned-bedtime cutoff helper**

  Add this helper below `resolveNextSleepType()` and before `dynamicHalfWindowMillis()`:

  ```kotlin
  private fun isWithinLearnedBedtimeCutoff(
      currentMinuteOfDay: Int?,
      bedtimeMinuteOfDay: Int?,
      ageInWeeks: Int,
  ): Boolean {
      if (currentMinuteOfDay == null || bedtimeMinuteOfDay == null) return false

      val thresholdMinutes = SleepAgePriors.getNapWakeWindowMidpoint(ageInWeeks).toMinutes().toInt()
      val minutesUntilBedtime = (bedtimeMinuteOfDay - currentMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
      val minutesSinceBedtime = (currentMinuteOfDay - bedtimeMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY

      return minutesUntilBedtime <= thresholdMinutes || minutesSinceBedtime <= thresholdMinutes
  }
  ```

- [ ] **Step 3: Add a minutes-per-day constant**

  Add this constant near the bottom of `SleepWindowPredictor`, after `computeFeedPrompt()` and before the final closing `}`:

  ```kotlin
  private const val MINUTES_PER_DAY = 1_440
  ```

  Do not modify `computeFeedPrompt()`. Put `private const val MINUTES_PER_DAY = 1_440` after the closing brace of `computeFeedPrompt()` and before the closing brace of `object SleepWindowPredictor`.

- [ ] **Step 4: Update `buildWindow()` to pass `features.currentMinuteOfDay`**

  Find in `buildWindow()`:

  ```kotlin
  val nextType = resolveNextSleepType(metrics, ageInWeeks)
  ```

  Replace with:

  ```kotlin
  val nextType = resolveNextSleepType(metrics, ageInWeeks, features.currentMinuteOfDay)
  ```

- [ ] **Step 5: Run the Task 5 tests to confirm all routing and production-path tests pass**

  ```
  ./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NIGHT_SLEEP for skipped nap before learned bedtime threshold" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NIGHT_SLEEP for skipped nap just after learned bedtime" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NIGHT_SLEEP for skipped nap after bedtime across midnight" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NAP for skipped nap when still far from bedtime" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.resolveNextSleepType returns NAP for skipped nap when no learned bedtime available" --tests "com.babytracker.domain.sleep.eval.SleepWindowPredictorTest.predict routes skipped nap to bedtime model using features currentMinuteOfDay"
  ```

  Expected: all six PASS.

- [ ] **Step 6: Run full unit test suite**

  ```
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7: Run ktlint and detekt**

  ```
  ./gradlew ktlintFormat detekt
  ```

  Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 8: Commit**

  ```
  git add app/src/main/java/com/babytracker/domain/sleep/eval/SleepWindowPredictor.kt \
          app/src/test/java/com/babytracker/domain/sleep/eval/SleepWindowPredictorTest.kt
  git commit -m "fix(sleep): route to NIGHT_SLEEP when skipped nap and within bedtime threshold [AKA-93]"
  ```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| Finding 1: replace combined-IQR gate with type-aware gating | Task 2 (impl) + Task 1 (tests) |
| Finding 1: add eval fixture with wide nap/bedtime separation | Task 3 |
| Finding 2: incorporate learned bedtime medians into resolveNextSleepType | Task 6 (impl) + Task 5 (tests) |
| Finding 2: add tests for skipped-nap late-day scenarios, including after learned bedtime and midnight wrap-around | Task 5 |
| Finding 2: prove `predict()` / `buildWindow()` threads `features.currentMinuteOfDay` into routing | Task 5 Step 3 + Task 6 Step 4 |
| `currentMinuteOfDay` data plumbing (required by Finding 2), including configured-zone behavior | Task 4 |

**Placeholder scan:** No TBDs, all code blocks are complete.

**Type consistency check:**
- `SleepFeatures.currentMinuteOfDay: Int?` — set in Task 4 Step 3, accessed in Task 6 Step 4 ✓
- `resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay: Int? = null)` — defined in Task 6 Step 1, called with named param in Task 5 tests ✓
- `isWithinLearnedBedtimeCutoff(currentMinuteOfDay: Int?, bedtimeMinuteOfDay: Int?, ageInWeeks: Int)` — defined in Task 6 Step 2, used in Task 6 Step 1 ✓
- `MINUTES_PER_DAY` — defined in Task 6 Step 3, used by the cutoff helper in Task 6 Step 2 ✓
- `SleepInterval.from(startMillis, endMillis, SleepType)` — used in Tasks 1 and 4; already exists in codebase ✓
- `metrics.medianBedtimeMinuteOfDay: Int?` — already in `SleepMetrics`; referenced in Task 6 Step 1 ✓
- `SleepAgePriors.getNapWakeWindowMidpoint(ageInWeeks).toMinutes().toInt()` — already exists in `SleepAgePriors`; converted to `Int` before comparing with minute-of-day deltas ✓
