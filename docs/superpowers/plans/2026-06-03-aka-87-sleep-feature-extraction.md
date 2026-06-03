# Sleep Feature-Extraction Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure feature-extraction layer in `domain/sleep/feature/` — converts raw `SleepRecord` / `BreastfeedingSession` lists into validated intervals, aggregate metrics, and an evidence-quality signal consumed by the sleep-window predictor (AKA-88).

**Architecture:** Six new files in `domain/sleep/feature/` (all zero Android imports): `SleepInterval` and `BreastfeedInterval` are per-record validated wrappers; `SleepMetrics` holds computed aggregates; `EvidenceQuality` holds the gating signal; `SleepFeatures` bundles all outputs; `SleepFeatureExtractor` (constructor-injected `Clock` + `ZoneId`) orchestrates conversion and computation. All numeric thresholds live in `domain/model/SleepPredictionTuning`. Local-day-derived features (`medianBedtimeMinuteOfDay`, `medianMorningWakeMinuteOfDay`, `localDayCoverage`) are computed but **excluded from the gating bar** in Phase 0 (timezone provenance is unqualified).

**Tech Stack:** Kotlin, `java.time.*` (`Instant`, `Clock`, `ZoneId`, `ZonedDateTime`, `LocalDate`), JUnit 5 (`runTest`-free — pure sync), no MockK (pure domain, no mocks needed)

---

## File Map

**Create (production):**
- `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- `app/src/main/java/com/babytracker/domain/sleep/feature/SleepInterval.kt`
- `app/src/main/java/com/babytracker/domain/sleep/feature/BreastfeedInterval.kt`
- `app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt`
- `app/src/main/java/com/babytracker/domain/sleep/feature/EvidenceQuality.kt`
- `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt`
- `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`

**Create (tests):**
- `app/src/test/java/com/babytracker/domain/sleep/feature/SleepIntervalTest.kt`
- `app/src/test/java/com/babytracker/domain/sleep/feature/BreastfeedIntervalTest.kt`
- `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

---

## Task 1: SleepPredictionTuning constants

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`
- Test inline in `SleepFeatureExtractorTest.kt` (no separate test file for a constants object)

- [ ] **Step 1: Write the failing test (constants existence check)**

Create `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SleepFeatureExtractorTest {

    private val nowInstant = Instant.parse("2024-06-15T14:00:00Z")
    private val clock = Clock.fixed(nowInstant, ZoneOffset.UTC)
    private val utcZone: ZoneId = ZoneId.of("UTC")
    private lateinit var extractor: SleepFeatureExtractor

    @BeforeEach
    fun setup() {
        extractor = SleepFeatureExtractor(clock, utcZone)
    }

    // ── constants smoke test ──────────────────────────────────────────────────

    @Test
    fun `SleepPredictionTuning constants are positive`() {
        assertTrue(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS > 0)
        assertTrue(SleepPredictionTuning.MIN_COMPLETED_INTERVALS > 0)
        assertTrue(SleepPredictionTuning.LOOKBACK_DAYS > 0)
        assertTrue(SleepPredictionTuning.MAX_NAP_DURATION_HOURS > 0)
        assertTrue(SleepPredictionTuning.HALF_WINDOW_MINUTES > 0)
        assertEquals("sleep-pred-baseline-1", SleepPredictionTuning.ALGORITHM_VERSION)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun hoursMs(h: Double): Long = (h * 3_600_000).toLong()

    private fun sleepRecord(
        startHoursAgo: Double,
        endHoursAgo: Double?,
        type: SleepType = SleepType.NAP,
    ): SleepRecord = SleepRecord(
        startTime = nowInstant.minusMillis(hoursMs(startHoursAgo)),
        endTime = endHoursAgo?.let { nowInstant.minusMillis(hoursMs(it)) },
        sleepType = type,
    )

    private fun feed(startHoursAgo: Double, endHoursAgo: Double?): BreastfeedingSession =
        BreastfeedingSession(
            startTime = nowInstant.minusMillis(hoursMs(startHoursAgo)),
            endTime = endHoursAgo?.let { nowInstant.minusMillis(hoursMs(it)) },
            startingSide = BreastSide.LEFT,
        )
}
```

- [ ] **Step 2: Run test — expect compilation failure** (`SleepPredictionTuning` not found)

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.SleepPredictionTuning constants are positive"
```

Expected: BUILD FAILED — `error: unresolved reference: SleepPredictionTuning`

- [ ] **Step 3: Create SleepPredictionTuning**

Create `app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt`:

```kotlin
package com.babytracker.domain.model

object SleepPredictionTuning {
    // ── feature extraction ────────────────────────────────────────────────────
    const val MAX_NAP_DURATION_HOURS = 4L
    const val LOOKBACK_DAYS = 14L
    const val FRESHNESS_HORIZON_HOURS = 12L
    const val MIN_COMPLETED_INTERVALS = 5
    const val MIN_LOCAL_DAYS = 3
    const val MIN_PLAUSIBLE_WAKE_INTERVAL_MINUTES = 15L
    const val MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS = 6L
    const val INSTABILITY_CEILING_MINUTES = 45L
    const val MAX_INVALID_RATE = 0.25f

    // ── baseline predictor ────────────────────────────────────────────────────
    const val HALF_WINDOW_MINUTES = 15L
    const val FULL_PERSONALIZATION_INTERVALS = 14
    const val OVERDUE_GRACE_MINUTES = 45L
    const val CUE_LED_MAX_AGE_WEEKS = 6

    // ── Phase 2+ ──────────────────────────────────────────────────────────────
    const val CANDIDATE_STEP_MINUTES = 5L

    // ── Phase 4 ───────────────────────────────────────────────────────────────
    const val SHRINK_N = 10
    const val MAX_BIAS_MINUTES = 15L

    // ── evaluation harness ────────────────────────────────────────────────────
    const val EVAL_MIN_ANCHORS = 20
    const val EVAL_MIN_MAE_GAIN_MIN = 5
    const val EVAL_MAX_REGRESSION = 0
    const val ALGORITHM_VERSION = "sleep-pred-baseline-1"
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest.SleepPredictionTuning constants are positive"
```

Expected: BUILD SUCCESSFUL, 1 test passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/SleepPredictionTuning.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add SleepPredictionTuning constants [AKA-87]"
```

---

## Task 2: SleepInterval validation

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepInterval.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepIntervalTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/babytracker/domain/sleep/feature/SleepIntervalTest.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SleepIntervalTest {

    private val base = 1_000_000_000L  // arbitrary epoch ms anchor

    @Test
    fun `valid completed nap returns interval`() {
        val dur = Duration.ofHours(1).toMillis()
        val result = SleepInterval.from(base, base + dur, SleepType.NAP)
        assertNull(result?.let { null } ?: null) // just ensure non-null
        assertTrue(result != null)
        assertEquals(base, result!!.startMillis)
        assertEquals(base + dur, result.endMillis)
        assertEquals(dur, result.durationMillis)
        assertTrue(result.isCompleted)
    }

    @Test
    fun `end equals start returns null`() {
        assertNull(SleepInterval.from(base, base, SleepType.NAP))
    }

    @Test
    fun `end before start returns null`() {
        assertNull(SleepInterval.from(base, base - 1, SleepType.NAP))
    }

    @Test
    fun `nap exactly 4 hours returns valid interval`() {
        val dur = Duration.ofHours(4).toMillis()
        val result = SleepInterval.from(base, base + dur, SleepType.NAP)
        assertTrue(result != null)
        assertEquals(dur, result!!.durationMillis)
    }

    @Test
    fun `nap just under 4 hours returns valid interval`() {
        val dur = Duration.ofHours(4).toMillis() - 1
        val result = SleepInterval.from(base, base + dur, SleepType.NAP)
        assertTrue(result != null)
    }

    @Test
    fun `night sleep longer than 4 hours is valid`() {
        val dur = Duration.ofHours(9).toMillis()
        val result = SleepInterval.from(base, base + dur, SleepType.NIGHT_SLEEP)
        assertTrue(result != null)
        assertEquals(dur, result!!.durationMillis)
    }

    @Test
    fun `open record with null end returns open interval`() {
        val result = SleepInterval.from(base, null, SleepType.NAP)
        assertTrue(result != null)
        assertNull(result!!.endMillis)
        assertNull(result.durationMillis)
        assertTrue(!result.isCompleted)
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepIntervalTest"
```

Expected: BUILD FAILED — `error: unresolved reference: SleepInterval`

- [ ] **Step 3: Implement SleepInterval**

Create `app/src/main/java/com/babytracker/domain/sleep/feature/SleepInterval.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Duration

data class SleepInterval(
    val startMillis: Long,
    val endMillis: Long?,
    val sleepType: SleepType,
) {
    val isCompleted: Boolean get() = endMillis != null
    val durationMillis: Long? get() = endMillis?.let { it - startMillis }

    companion object {
        private val maxNapMillis = Duration.ofHours(SleepPredictionTuning.MAX_NAP_DURATION_HOURS).toMillis()

        fun from(record: SleepRecord): SleepInterval? =
            from(record.startTime.toEpochMilli(), record.endTime?.toEpochMilli(), record.sleepType)

        fun from(startMillis: Long, endMillis: Long?, sleepType: SleepType): SleepInterval? {
            if (endMillis != null) {
                if (endMillis <= startMillis) return null
                if (sleepType == SleepType.NAP && endMillis - startMillis > maxNapMillis) return null
            }
            return SleepInterval(startMillis, endMillis, sleepType)
        }
    }
}
```

- [ ] **Step 4: Run — expect all 7 tests PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepIntervalTest"
```

Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepInterval.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepIntervalTest.kt
git commit -m "feat(sleep): add SleepInterval with per-record validation [AKA-87]"
```

---

## Task 3: BreastfeedInterval validation

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/feature/BreastfeedInterval.kt`
- Create: `app/src/test/java/com/babytracker/domain/sleep/feature/BreastfeedIntervalTest.kt`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/babytracker/domain/sleep/feature/BreastfeedIntervalTest.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedIntervalTest {

    private val baseInstant = Instant.ofEpochMilli(1_000_000_000L)

    private fun session(startMs: Long, endMs: Long?) = BreastfeedingSession(
        startTime = Instant.ofEpochMilli(startMs),
        endTime = endMs?.let { Instant.ofEpochMilli(it) },
        startingSide = BreastSide.LEFT,
    )

    @Test
    fun `valid completed session returns interval`() {
        val result = BreastfeedInterval.from(session(1000L, 4600L))
        assertTrue(result != null)
        assertFalse(result!!.isActive)
        assertTrue(result.isCompleted)
    }

    @Test
    fun `session end before start returns null`() {
        assertNull(BreastfeedInterval.from(session(5000L, 3000L)))
    }

    @Test
    fun `session end equals start returns null`() {
        assertNull(BreastfeedInterval.from(session(5000L, 5000L)))
    }

    @Test
    fun `open session returns active interval`() {
        val result = BreastfeedInterval.from(session(1000L, null))
        assertTrue(result != null)
        assertTrue(result!!.isActive)
        assertNull(result.endMillis)
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.BreastfeedIntervalTest"
```

Expected: BUILD FAILED — `error: unresolved reference: BreastfeedInterval`

- [ ] **Step 3: Implement BreastfeedInterval**

Create `app/src/main/java/com/babytracker/domain/sleep/feature/BreastfeedInterval.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastfeedingSession

data class BreastfeedInterval(
    val startMillis: Long,
    val endMillis: Long?,
) {
    val isActive: Boolean get() = endMillis == null
    val isCompleted: Boolean get() = endMillis != null

    companion object {
        fun from(session: BreastfeedingSession): BreastfeedInterval? {
            val startMs = session.startTime.toEpochMilli()
            val endMs = session.endTime?.toEpochMilli()
            if (endMs != null && endMs <= startMs) return null
            return BreastfeedInterval(startMs, endMs)
        }
    }
}
```

- [ ] **Step 4: Run — expect 4 tests PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.BreastfeedIntervalTest"
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/BreastfeedInterval.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/BreastfeedIntervalTest.kt
git commit -m "feat(sleep): add BreastfeedInterval with validation [AKA-87]"
```

---

## Task 4: SleepFeatureExtractor — buildSleepIntervals + buildBreastfeedIntervals

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt` (partial — just the two build methods)
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1: Add failing tests for buildSleepIntervals to SleepFeatureExtractorTest**

Append these tests to `SleepFeatureExtractorTest.kt` (inside the class, after the existing test):

```kotlin
    // ── buildSleepIntervals ───────────────────────────────────────────────────

    @Test
    fun `buildSleepIntervals filters per-record invalid entries`() {
        val records = listOf(
            sleepRecord(3.0, 2.0),          // valid 1h nap
            sleepRecord(5.0, 5.0),          // end == start → invalid
            sleepRecord(7.0, 6.9),          // end before start → invalid (0.1h ago ends before 6.9h ago start)
        )
        // Note: 5.0h ago start with 5.0h ago end = zero duration = invalid
        // 7.0h ago start, 6.9h ago end: start=7h before now, end=6.9h before now → end > start → actually valid 6min nap
        // Let's use a clearly-before-start pair:
        val records2 = listOf(
            sleepRecord(3.0, 2.0),                      // valid 1h nap
            sleepRecord(startHoursAgo = 5.0, endHoursAgo = 5.0),  // zero duration
            sleepRecord(startHoursAgo = 7.0, endHoursAgo = 8.0),  // end (8h ago) < start (7h ago) → invalid
        )
        val result = extractor.buildSleepIntervals(records2)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildSleepIntervals removes overlapping records keeping earlier one`() {
        val records = listOf(
            sleepRecord(4.0, 2.0),  // 4h ago → 2h ago (2h nap)
            sleepRecord(3.0, 1.0),  // 3h ago → 1h ago — starts inside first, overlap
        )
        val result = extractor.buildSleepIntervals(records)
        assertEquals(1, result.size)
        // The earlier-starting record is kept
        val kept = result.first()
        assertEquals(nowInstant.minusMillis(hoursMs(4.0)).toEpochMilli(), kept.startMillis)
    }

    @Test
    fun `buildSleepIntervals keeps adjacent non-overlapping records`() {
        val records = listOf(
            sleepRecord(6.0, 4.0),  // 6h ago → 4h ago
            sleepRecord(4.0, 2.0),  // 4h ago → 2h ago (starts exactly when previous ends)
        )
        val result = extractor.buildSleepIntervals(records)
        assertEquals(2, result.size)
    }

    @Test
    fun `buildSleepIntervals keeps current open sleep after completed history`() {
        val records = listOf(
            sleepRecord(3.0, 2.0),  // completed nap
            sleepRecord(1.0, null), // current active nap after the completed nap
        )
        val result = extractor.buildSleepIntervals(records)
        assertEquals(2, result.size)
        assertTrue(result.any { !it.isCompleted })
    }

    @Test
    fun `buildSleepIntervals drops stale open sleep without dropping later completed history`() {
        val records = listOf(
            sleepRecord(5.0, null), // stale open record that overlaps later completed records
            sleepRecord(3.0, 2.0),
            sleepRecord(1.5, 0.5),
        )
        val result = extractor.buildSleepIntervals(records)
        assertEquals(2, result.size)
        assertTrue(result.all { it.isCompleted })
    }

    @Test
    fun `buildSleepIntervals handles empty list`() {
        assertTrue(extractor.buildSleepIntervals(emptyList()).isEmpty())
    }

    @Test
    fun `buildBreastfeedIntervals filters invalid sessions`() {
        val sessions = listOf(
            feed(2.0, 1.75),        // valid 15-min feed
            feed(4.0, 4.5),         // end (4.5h ago) < start (4h ago) → invalid
        )
        val result = extractor.buildBreastfeedIntervals(sessions)
        assertEquals(1, result.size)
    }
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD FAILED — `error: unresolved reference: SleepFeatureExtractor`

- [ ] **Step 3: Create SleepFeatureExtractor with the two build methods**

Create `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import java.time.Clock
import java.time.ZoneId

class SleepFeatureExtractor(
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    fun buildSleepIntervals(records: List<SleepRecord>): List<SleepInterval> {
        val perRecordValid = records.mapNotNull { SleepInterval.from(it) }
        val completed = perRecordValid
            .filter { it.isCompleted }
            .sortedBy { it.startMillis }
            .let { removeOverlapping(it) }
        val latestOpen = perRecordValid
            .filter { !it.isCompleted }
            .maxByOrNull { it.startMillis }
        val nonOverlappingOpen = listOfNotNull(latestOpen)
            .filter { openInterval ->
                completed.none { completedInterval ->
                    completedInterval.endMillis!! > openInterval.startMillis
                }
            }
        return (completed + nonOverlappingOpen).sortedBy { it.startMillis }
    }

    fun buildBreastfeedIntervals(sessions: List<BreastfeedingSession>): List<BreastfeedInterval> =
        sessions.mapNotNull { BreastfeedInterval.from(it) }.sortedBy { it.startMillis }

    private fun removeOverlapping(sortedCompleted: List<SleepInterval>): List<SleepInterval> {
        val result = mutableListOf<SleepInterval>()
        for (interval in sortedCompleted) {
            if (result.isEmpty()) {
                result.add(interval)
                continue
            }
            val lastEnd = result.last().endMillis!!
            if (interval.startMillis >= lastEnd) result.add(interval)
        }
        return result
    }
}
```

- [ ] **Step 4: Run tests — expect all to PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add SleepFeatureExtractor with interval building and overlap removal [AKA-87]"
```

---

## Task 5: SleepMetrics + computeMetrics

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1: Create SleepMetrics data class**

Create `app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepType

data class SleepMetrics(
    val lastWakeMillis: Long?,
    val lastSleepType: SleepType?,
    val lastSleepDurationMillis: Long?,
    val completedWakeIntervals: List<Long>,
    val medianWakeIntervalMillis: Long?,
    val wakeIntervalIqrMillis: Long?,
    val sleepLast24hMillis: Long,
    val daySleepTodayMillis: Long,
    val napCountToday: Int,
    val medianBedtimeMinuteOfDay: Int?,    // UNQUALIFIED Phase 0 — excluded from gating bar
    val medianMorningWakeMinuteOfDay: Int?, // UNQUALIFIED Phase 0 — excluded from gating bar
)
```

- [ ] **Step 2: Add failing computeMetrics tests to SleepFeatureExtractorTest**

Append these tests to `SleepFeatureExtractorTest.kt`:

```kotlin
    // ── computeMetrics ────────────────────────────────────────────────────────

    @Test
    fun `computeMetrics with no records returns empty metrics`() {
        val metrics = extractor.computeMetrics(emptyList())
        assertNull(metrics.lastWakeMillis)
        assertNull(metrics.lastSleepType)
        assertNull(metrics.lastSleepDurationMillis)
        assertTrue(metrics.completedWakeIntervals.isEmpty())
        assertNull(metrics.medianWakeIntervalMillis)
        assertNull(metrics.wakeIntervalIqrMillis)
        assertEquals(0L, metrics.sleepLast24hMillis)
        assertEquals(0L, metrics.daySleepTodayMillis)
        assertEquals(0, metrics.napCountToday)
        assertNull(metrics.medianBedtimeMinuteOfDay)
        assertNull(metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `computeMetrics reports lastWake as end of most recent completed sleep`() {
        val nap1 = SleepInterval.from(hoursMs(4.0).let { nowInstant.minusMillis(it).toEpochMilli() },
            hoursMs(3.0).let { nowInstant.minusMillis(it).toEpochMilli() }, SleepType.NAP)!!
        val nap2 = SleepInterval.from(hoursMs(2.0).let { nowInstant.minusMillis(it).toEpochMilli() },
            hoursMs(1.0).let { nowInstant.minusMillis(it).toEpochMilli() }, SleepType.NAP)!!
        val metrics = extractor.computeMetrics(listOf(nap1, nap2))
        assertEquals(nowInstant.minusMillis(hoursMs(1.0)).toEpochMilli(), metrics.lastWakeMillis)
        assertEquals(SleepType.NAP, metrics.lastSleepType)
        assertEquals(hoursMs(1.0), metrics.lastSleepDurationMillis)
    }

    @Test
    fun `computeMetrics computes one wake interval from two consecutive completed sleeps`() {
        // Sleep A: 5h ago → 4h ago (1h nap). Sleep B: 2h ago → 1h ago (1h nap).
        // Wake interval = 4h ago to 2h ago = 2h = 7_200_000 ms.
        val napA = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(5.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(4.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val napB = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(2.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(1.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val metrics = extractor.computeMetrics(listOf(napA, napB))
        assertEquals(1, metrics.completedWakeIntervals.size)
        assertEquals(hoursMs(2.0), metrics.completedWakeIntervals[0])
        assertEquals(hoursMs(2.0), metrics.medianWakeIntervalMillis)
    }

    @Test
    fun `computeMetrics filters wake interval outside plausible range`() {
        // Wake gap of 7h exceeds MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS (6h) → filtered out
        val napA = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(10.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(9.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val napB = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(2.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(1.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val metrics = extractor.computeMetrics(listOf(napA, napB))
        // gap = 9h ago → 2h ago = 7h → exceeds max → filtered
        assertTrue(metrics.completedWakeIntervals.isEmpty())
        assertNull(metrics.medianWakeIntervalMillis)
    }

    @Test
    fun `computeMetrics computes IQR over 5 wake intervals`() {
        // Build 6 sleeps → 5 wake intervals of 90, 100, 110, 120, 130 minutes
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = listOf(90, 100, 110, 120, 130),
            sleepDurationMinutes = 60,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(intervals)
        assertEquals(5, metrics.completedWakeIntervals.size)
        // median = 110min, Q1 = 100min, Q3 = 120min → IQR = 20min = 1_200_000 ms
        assertEquals(110 * 60_000L, metrics.medianWakeIntervalMillis)
        assertEquals(20 * 60_000L, metrics.wakeIntervalIqrMillis)
    }

    @Test
    fun `computeMetrics sleepLast24h sums overlap with last 24h window cross-midnight safe`() {
        // Sleep entirely within 24h: 3h ago → 2h ago (1h) → contributes 1h
        // Sleep partly outside: 25h ago → 23h ago → 1h overlap within the 24h window
        val recentNap = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(3.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(2.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val partlyOld = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(25.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(23.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val metrics = extractor.computeMetrics(listOf(recentNap, partlyOld))
        // recentNap = 1h, partlyOld contributes 1h (23h ago is inside 24h window)
        assertEquals(hoursMs(2.0), metrics.sleepLast24hMillis)
    }

    @Test
    fun `computeMetrics napCountToday counts only naps in local today`() {
        // "now" is 2024-06-15T14:00:00Z. Today in UTC starts at midnight.
        // Nap at 10:00→11:00 today = 4h ago → 3h ago (UTC)
        // Nap at 08:00→09:00 yesterday = 30h ago → 29h ago (UTC) — should NOT count
        val todayNap = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(4.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(3.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val yesterdayNap = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(30.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(29.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        val metrics = extractor.computeMetrics(listOf(todayNap, yesterdayNap))
        assertEquals(1, metrics.napCountToday)
        assertEquals(hoursMs(1.0), metrics.daySleepTodayMillis)
    }

    @Test
    fun `computeMetrics medianBedtimeMinuteOfDay computed from night sleeps (unqualified)`() {
        // Two night sleeps starting at 20:00 UTC (1200 min-of-day) and 21:00 UTC (1260 min-of-day)
        // now = 14:00 today; night sleeps were yesterday and day before
        val night1 = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(18.0)).toEpochMilli(), // 20:00 yesterday UTC
            nowInstant.minusMillis(hoursMs(8.0)).toEpochMilli(),  // 06:00 today UTC
            SleepType.NIGHT_SLEEP,
        )!!
        val night2 = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(42.0)).toEpochMilli(), // 20:00 2 days ago UTC
            nowInstant.minusMillis(hoursMs(32.0)).toEpochMilli(), // 06:00 yesterday UTC
            SleepType.NIGHT_SLEEP,
        )!!
        val metrics = extractor.computeMetrics(listOf(night1, night2))
        // Both nights start at 20:00 UTC = 20*60=1200 min-of-day
        assertEquals(1200, metrics.medianBedtimeMinuteOfDay)
    }

    // Helper: builds N+1 sleep intervals producing N consecutive wake intervals of the given durations.
    // Starts at `endHoursAgo` and works backward from "now".
    private fun buildConsecutiveSleeps(
        wakeIntervalsMinutes: List<Long>,
        sleepDurationMinutes: Long,
        endHoursAgo: Double,
    ): List<SleepInterval> {
        val sleepMs = sleepDurationMinutes * 60_000L
        val result = mutableListOf<SleepInterval>()
        var curEnd = nowInstant.toEpochMilli() - hoursMs(endHoursAgo)
        for (wakeMs in wakeIntervalsMinutes.reversed()) {
            val start = curEnd - sleepMs
            result.add(SleepInterval.from(start, curEnd, SleepType.NAP)!!)
            curEnd = start - wakeMs * 60_000L
        }
        // add the first sleep (anchor)
        result.add(SleepInterval.from(curEnd - sleepMs, curEnd, SleepType.NAP)!!)
        return result.sortedBy { it.startMillis }
    }
```

- [ ] **Step 3: Run — expect compilation failure** (`computeMetrics` not found)

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD FAILED — `error: unresolved reference: computeMetrics`

- [ ] **Step 4: Add computeMetrics to SleepFeatureExtractor**

Add to `SleepFeatureExtractor.kt` (below `buildBreastfeedIntervals`, before `removeOverlapping`):

```kotlin
    fun computeMetrics(validIntervals: List<SleepInterval>): SleepMetrics {
        val nowMillis = clock.instant().toEpochMilli()
        val todayLocalDate = java.time.LocalDate.now(clock.withZone(zoneId))
        val lookbackStartMillis = nowMillis - java.time.Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()

        val completed = validIntervals.filter { it.isCompleted }.sortedBy { it.startMillis }

        // last wake
        val lastCompleted = completed.maxByOrNull { it.endMillis!! }
        val lastWakeMillis = lastCompleted?.endMillis
        val lastSleepType = lastCompleted?.sleepType
        val lastSleepDurationMillis = lastCompleted?.durationMillis

        // wake → sleep intervals (gap between consecutive completed sleeps, within lookback + plausible range)
        val minPlausibleMs = java.time.Duration.ofMinutes(SleepPredictionTuning.MIN_PLAUSIBLE_WAKE_INTERVAL_MINUTES).toMillis()
        val maxPlausibleMs = java.time.Duration.ofHours(SleepPredictionTuning.MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS).toMillis()
        val wakeIntervals = mutableListOf<Long>()
        for (i in 0 until completed.size - 1) {
            val gapEnd = completed[i + 1].startMillis
            if (gapEnd < lookbackStartMillis) continue
            val gap = gapEnd - completed[i].endMillis!!
            if (gap in minPlausibleMs..maxPlausibleMs) wakeIntervals.add(gap)
        }

        val medianWake = if (wakeIntervals.isNotEmpty()) medianMillis(wakeIntervals.sorted()) else null
        val iqr = if (wakeIntervals.size >= 2) iqrMillis(wakeIntervals) else null

        // sleepLast24h — interval overlap, cross-midnight safe
        val window24hStart = nowMillis - java.time.Duration.ofHours(24).toMillis()
        val sleepLast24h = completed.sumOf { iv ->
            val s = maxOf(iv.startMillis, window24hStart)
            val e = minOf(iv.endMillis!!, nowMillis)
            if (e > s) e - s else 0L
        }

        // daySleepToday + napCountToday (local day via ZoneId)
        val todayStartMillis = todayLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val todayEndMillis = todayLocalDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val napsToday = completed.filter { it.sleepType == SleepType.NAP }
        val daySleepToday = napsToday.sumOf { iv ->
            val s = maxOf(iv.startMillis, todayStartMillis)
            val e = minOf(iv.endMillis!!, todayEndMillis)
            if (e > s) e - s else 0L
        }
        val napCountToday = napsToday.count { it.endMillis!! > todayStartMillis && it.startMillis < todayEndMillis }

        // medianBedtime + medianMorningWake — UNQUALIFIED Phase 0
        val recentNights = completed.filter { it.sleepType == SleepType.NIGHT_SLEEP && it.startMillis >= lookbackStartMillis }
        val bedtimesMin = recentNights.map {
            java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.startMillis), zoneId).let { zdt ->
                zdt.hour * 60 + zdt.minute
            }
        }
        val morningWakesMin = recentNights.map {
            java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.endMillis!!), zoneId).let { zdt ->
                zdt.hour * 60 + zdt.minute
            }
        }

        return SleepMetrics(
            lastWakeMillis = lastWakeMillis,
            lastSleepType = lastSleepType,
            lastSleepDurationMillis = lastSleepDurationMillis,
            completedWakeIntervals = wakeIntervals,
            medianWakeIntervalMillis = medianWake,
            wakeIntervalIqrMillis = iqr,
            sleepLast24hMillis = sleepLast24h,
            daySleepTodayMillis = daySleepToday,
            napCountToday = napCountToday,
            medianBedtimeMinuteOfDay = if (bedtimesMin.isNotEmpty()) medianInt(bedtimesMin.sorted()) else null,
            medianMorningWakeMinuteOfDay = if (morningWakesMin.isNotEmpty()) medianInt(morningWakesMin.sorted()) else null,
        )
    }
```

Also add the needed imports at the top of `SleepFeatureExtractor.kt`:

```kotlin
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
```

And add the private helper methods inside the class (after `removeOverlapping`):

```kotlin
    private fun medianMillis(sorted: List<Long>): Long {
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun medianInt(sorted: List<Int>): Int {
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun iqrMillis(values: List<Long>): Long {
        val sorted = values.sorted()
        return percentileMillis(sorted, 75) - percentileMillis(sorted, 25)
    }

    private fun percentileMillis(sorted: List<Long>, pct: Int): Long {
        val idx = (sorted.size - 1) * pct / 100.0
        val lower = idx.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        val frac = idx - lower
        return sorted[lower] + (frac * (sorted[upper] - sorted[lower])).toLong()
    }
```

- [ ] **Step 5: Run — expect all tests PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: ktlintFormat + detekt**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: both succeed with no violations

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepMetrics.kt
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add SleepMetrics and computeMetrics to SleepFeatureExtractor [AKA-87]"
```

---

## Task 6: EvidenceQuality + computeQuality

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/feature/EvidenceQuality.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1: Create EvidenceQuality data class**

Create `app/src/main/java/com/babytracker/domain/sleep/feature/EvidenceQuality.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

data class EvidenceQuality(
    val lastWakeRecencyMillis: Long?,
    val isFresh: Boolean,
    val completedIntervalCount: Int,
    val localDayCoverage: Int,          // UNQUALIFIED Phase 0 — excluded from passesGatingBar
    val wakeIntervalIqrMillis: Long?,
    val invalidRecordRate: Float,
    val passesGatingBar: Boolean,
)
```

- [ ] **Step 2: Add failing computeQuality tests to SleepFeatureExtractorTest**

Append to `SleepFeatureExtractorTest.kt`:

```kotlin
    // ── computeQuality ────────────────────────────────────────────────────────

    @Test
    fun `computeQuality passes gating bar with sufficient fresh intervals and low invalid rate`() {
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(5) { 120L },
            sleepDurationMinutes = 90,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, rawRecordCount = intervals.size, metrics = metrics)
        assertTrue(quality.isFresh)
        assertEquals(5, quality.completedIntervalCount)
        assertEquals(0f, quality.invalidRecordRate)
        assertTrue(quality.passesGatingBar)
    }

    @Test
    fun `computeQuality fails gating bar when last wake is stale`() {
        // Last sleep ended 13h ago (> FRESHNESS_HORIZON_HOURS = 12)
        val nap = SleepInterval.from(
            nowInstant.minusMillis(hoursMs(14.0)).toEpochMilli(),
            nowInstant.minusMillis(hoursMs(13.0)).toEpochMilli(),
            SleepType.NAP,
        )!!
        // Add 4 more intervals deep in the past
        val oldIntervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(4) { 120L },
            sleepDurationMinutes = 60,
            endHoursAgo = 13.5,
        )
        val all = (oldIntervals + nap).sortedBy { it.startMillis }
        val metrics = extractor.computeMetrics(all)
        val quality = extractor.computeQuality(all, rawRecordCount = all.size, metrics = metrics)
        assertFalse(quality.isFresh)
        assertFalse(quality.passesGatingBar)
    }

    @Test
    fun `computeQuality fails gating bar when interval count is below minimum`() {
        // Only 4 intervals, MIN_COMPLETED_INTERVALS = 5
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(4) { 120L },
            sleepDurationMinutes = 90,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, rawRecordCount = intervals.size, metrics = metrics)
        assertEquals(4, quality.completedIntervalCount)
        assertFalse(quality.passesGatingBar)
    }

    @Test
    fun `computeQuality fails gating bar when IQR exceeds instability ceiling`() {
        // IQR of 60 minutes exceeds INSTABILITY_CEILING_MINUTES (45)
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = listOf(60L, 60L, 120L, 120L, 180L),
            sleepDurationMinutes = 90,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(intervals)
        // Q1=60, Q3=120, IQR=60 min = 3_600_000 ms → exceeds 45min ceiling
        val quality = extractor.computeQuality(intervals, rawRecordCount = intervals.size, metrics = metrics)
        assertTrue(quality.wakeIntervalIqrMillis != null && quality.wakeIntervalIqrMillis!! > 45 * 60_000L)
        assertFalse(quality.passesGatingBar)
    }

    @Test
    fun `computeQuality fails gating bar when invalid record rate is too high`() {
        val validIntervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(5) { 120L },
            sleepDurationMinutes = 90,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(validIntervals)
        // 6 valid records but raw count was 9 → 3 invalid → rate = 3/9 = 0.33 > 0.25 max
        val quality = extractor.computeQuality(validIntervals, rawRecordCount = 9, metrics = metrics)
        assertTrue(quality.invalidRecordRate > SleepPredictionTuning.MAX_INVALID_RATE)
        assertFalse(quality.passesGatingBar)
    }

    @Test
    fun `computeQuality localDayCoverage is computed but does not affect passesGatingBar`() {
        // All 6 sleeps fit within one UTC local day — localDayCoverage = 1 < MIN_LOCAL_DAYS (3)
        // But gating bar should still pass because local-day coverage is excluded from bar in Phase 0
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(5) { 60L },  // 5 intervals × 60min each
            sleepDurationMinutes = 60,
            endHoursAgo = 0.25,
        )
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, rawRecordCount = intervals.size, metrics = metrics)
        assertTrue(quality.passesGatingBar, "Gating bar must not require local-day coverage in Phase 0")
    }
```

- [ ] **Step 3: Run — expect compilation failure** (`computeQuality` not found)

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD FAILED — `error: unresolved reference: computeQuality`

- [ ] **Step 4: Add computeQuality to SleepFeatureExtractor**

Add to `SleepFeatureExtractor.kt` (after `computeMetrics`):

```kotlin
    fun computeQuality(
        validIntervals: List<SleepInterval>,
        rawRecordCount: Int,
        metrics: SleepMetrics,
    ): EvidenceQuality {
        val nowMillis = clock.instant().toEpochMilli()
        val freshnessHorizonMillis = Duration.ofHours(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS).toMillis()
        val lookbackStartMillis = nowMillis - Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()

        val recencyMillis = metrics.lastWakeMillis?.let { nowMillis - it }
        val isFresh = recencyMillis != null && recencyMillis < freshnessHorizonMillis

        val completedIntervalCount = metrics.completedWakeIntervals.size

        val localDayCoverage = validIntervals
            .filter { it.isCompleted && it.endMillis!! >= lookbackStartMillis }
            .map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startMillis), zoneId) }
            .toSet().size

        val invalidCount = rawRecordCount - validIntervals.size
        val invalidRecordRate = if (rawRecordCount > 0) invalidCount.toFloat() / rawRecordCount else 0f

        val instabilityCeilingMillis = Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES).toMillis()

        val passesGatingBar = isFresh &&
            completedIntervalCount >= SleepPredictionTuning.MIN_COMPLETED_INTERVALS &&
            (metrics.wakeIntervalIqrMillis == null || metrics.wakeIntervalIqrMillis <= instabilityCeilingMillis) &&
            invalidRecordRate < SleepPredictionTuning.MAX_INVALID_RATE

        return EvidenceQuality(
            lastWakeRecencyMillis = recencyMillis,
            isFresh = isFresh,
            completedIntervalCount = completedIntervalCount,
            localDayCoverage = localDayCoverage,
            wakeIntervalIqrMillis = metrics.wakeIntervalIqrMillis,
            invalidRecordRate = invalidRecordRate,
            passesGatingBar = passesGatingBar,
        )
    }
```

- [ ] **Step 5: Run — expect all tests PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: ktlintFormat + detekt**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: no violations

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/EvidenceQuality.kt
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add EvidenceQuality and computeQuality to SleepFeatureExtractor [AKA-87]"
```

---

## Task 7: SleepFeatures + extract() + sparse-logger / DST / overlap integration tests

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt`
- Modify: `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt`
- Modify: `app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt`

- [ ] **Step 1: Create SleepFeatures bundle**

Create `app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt`:

```kotlin
package com.babytracker.domain.sleep.feature

data class SleepFeatures(
    val validIntervals: List<SleepInterval>,
    val feedIntervals: List<BreastfeedInterval>,
    val metrics: SleepMetrics,
    val quality: EvidenceQuality,
)
```

- [ ] **Step 2: Add failing extract() + edge-case tests**

Append to `SleepFeatureExtractorTest.kt`:

```kotlin
    // ── extract() integration ─────────────────────────────────────────────────

    @Test
    fun `extract bundles valid intervals metrics and quality`() {
        val records = listOf(
            sleepRecord(5.0, 4.0),  // valid
            sleepRecord(5.0, 5.0),  // invalid — zero duration
            sleepRecord(3.0, 2.0),  // valid
        )
        val feeds = listOf(feed(1.5, 1.25))
        val features = extractor.extract(records, feeds)
        assertEquals(2, features.validIntervals.size)
        assertEquals(1, features.feedIntervals.size)
        assertNotNull(features.metrics)
        assertNotNull(features.quality)
        // 1 invalid out of 3 raw → rate = 1/3 ≈ 0.33
        assertTrue(features.quality.invalidRecordRate > 0f)
    }

    // ── sparse-logger scenarios ───────────────────────────────────────────────

    @Test
    fun `sparse logger one sleep per day for 5 days produces 4 intervals`() {
        // 5 1-hour naps, one per day, spaced 24h apart
        val records = (0..4).map { day ->
            sleepRecord(
                startHoursAgo = (day * 24 + 6).toDouble(),  // each nap starts at 08:00 local
                endHoursAgo = (day * 24 + 5).toDouble(),    // ends at 09:00 local
            )
        }
        val features = extractor.extract(records, emptyList())
        assertEquals(5, features.validIntervals.size)
        // 4 gaps, all ~23h apart — exceeds max plausible wake (6h), so no intervals in range
        // Spec says plausible range is 15min–6h; 23h gaps are filtered → 0 wake intervals
        assertEquals(0, features.quality.completedIntervalCount)
        assertFalse(features.quality.passesGatingBar)
    }

    @Test
    fun `nights only: zero napCountToday and zero daySleepToday`() {
        // 3 night sleeps, none today (all ended > 8h ago)
        val nights = (1..3).map { day ->
            sleepRecord(
                startHoursAgo = (day * 24 + 14).toDouble(),
                endHoursAgo = (day * 24 + 6).toDouble(),
                type = SleepType.NIGHT_SLEEP,
            )
        }
        val features = extractor.extract(nights, emptyList())
        assertEquals(0, features.metrics.napCountToday)
        assertEquals(0L, features.metrics.daySleepTodayMillis)
        assertNotNull(features.metrics.medianBedtimeMinuteOfDay)
        assertNotNull(features.metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `naps only: null medianBedtime and null medianMorningWake`() {
        val naps = listOf(
            sleepRecord(4.0, 3.0),
            sleepRecord(2.0, 1.0),
        )
        val features = extractor.extract(naps, emptyList())
        assertNull(features.metrics.medianBedtimeMinuteOfDay)
        assertNull(features.metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `stale last wake fails freshness check`() {
        // Last sleep ended 13h ago (> FRESHNESS_HORIZON_HOURS = 12)
        val records = listOf(sleepRecord(14.0, 13.0))
        val features = extractor.extract(records, emptyList())
        assertFalse(features.quality.isFresh)
        assertTrue(features.quality.lastWakeRecencyMillis != null)
        assertTrue(features.quality.lastWakeRecencyMillis!! > 12 * 3_600_000L)
    }

    @Test
    fun `overlapping records counted in invalid rate`() {
        // 3 raw records, 1 overlap removed → 2 valid, 1 invalid → rate = 1/3
        val records = listOf(
            sleepRecord(4.0, 2.0),  // valid: 4h→2h
            sleepRecord(3.0, 1.0),  // overlap (starts inside first) → removed
            sleepRecord(1.5, 0.5),  // valid: 1.5h→0.5h
        )
        val features = extractor.extract(records, emptyList())
        assertEquals(2, features.validIntervals.size)
        // invalid = 3 - 2 = 1; rate = 1/3 ≈ 0.333
        assertTrue(features.quality.invalidRecordRate > 0.30f)
    }

    // ── DST + timezone tests ──────────────────────────────────────────────────

    @Test
    fun `sleepLast24h is epoch-delta based and unaffected by DST spring-forward`() {
        // Spring forward: 2024-03-10T02:00 US/Eastern → 03:00 (clocks skip 1h)
        // A sleep from 01:30 to 03:30 local ET spans the gap — actual epoch duration = 1h
        // nowInstant fixed at 2024-03-10T12:00:00-04:00 (after DST switch)
        val dstClock = Clock.fixed(
            Instant.parse("2024-03-10T16:00:00Z"),  // 12:00 EDT (UTC-4)
            ZoneOffset.UTC,
        )
        val dstZone = ZoneId.of("America/New_York")
        val dstExtractor = SleepFeatureExtractor(dstClock, dstZone)

        // Sleep: 01:30 ET (= 06:30 UTC) to 03:30 ET (= 07:30 UTC) = 1h epoch duration
        val sleepStart = Instant.parse("2024-03-10T06:30:00Z").toEpochMilli()
        val sleepEnd = Instant.parse("2024-03-10T07:30:00Z").toEpochMilli()
        val interval = SleepInterval.from(sleepStart, sleepEnd, SleepType.NIGHT_SLEEP)!!

        val metrics = dstExtractor.computeMetrics(listOf(interval))
        // Epoch duration = 1h = 3_600_000 ms
        assertEquals(3_600_000L, metrics.sleepLast24hMillis)
    }

    @Test
    fun `zone change between record creation and extraction uses injected zone`() {
        // Records created in Asia/Tokyo (UTC+9), extraction in Europe/London (UTC+1)
        // "now" = 2024-06-15T10:00:00 Europe/London = 2024-06-15T09:00:00Z
        val londonClock = Clock.fixed(Instant.parse("2024-06-15T09:00:00Z"), ZoneOffset.UTC)
        val londonZone = ZoneId.of("Europe/London")
        val londonExtractor = SleepFeatureExtractor(londonClock, londonZone)

        // Nap at 08:00 London today (= 07:00 UTC) → 09:00 London (= 08:00 UTC)
        val interval = SleepInterval.from(
            Instant.parse("2024-06-15T07:00:00Z").toEpochMilli(),
            Instant.parse("2024-06-15T08:00:00Z").toEpochMilli(),
            SleepType.NAP,
        )!!
        val metrics = londonExtractor.computeMetrics(listOf(interval))
        // 1h nap is in London "today"
        assertEquals(1, metrics.napCountToday)
        assertEquals(3_600_000L, metrics.daySleepTodayMillis)
    }

    @Test
    fun `open in-progress sleep does not contribute to lastWake or wake intervals`() {
        val features = extractor.extract(
            listOf(
                sleepRecord(3.0, 2.0),  // completed nap
                sleepRecord(1.0, null), // active nap started 1h ago
            ),
            emptyList(),
        )
        // lastWake = end of completed nap 2h ago; active sleep has null end
        assertTrue(features.validIntervals.any { !it.isCompleted })
        assertEquals(nowInstant.minusMillis(hoursMs(2.0)).toEpochMilli(), features.metrics.lastWakeMillis)
    }

    @Test
    fun `stale open sleep does not erase completed history during extract`() {
        val features = extractor.extract(
            listOf(
                sleepRecord(5.0, null), // stale open record overlapping later completed records
                sleepRecord(3.0, 2.0),
                sleepRecord(1.5, 0.5),
            ),
            emptyList(),
        )
        assertEquals(2, features.validIntervals.size)
        assertTrue(features.validIntervals.all { it.isCompleted })
        assertEquals(nowInstant.minusMillis(hoursMs(0.5)).toEpochMilli(), features.metrics.lastWakeMillis)
    }
```

- [ ] **Step 3: Run — expect compilation failure** (`extract` not found)

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.SleepFeatureExtractorTest"
```

Expected: BUILD FAILED — `error: unresolved reference: extract`

- [ ] **Step 4: Add extract() to SleepFeatureExtractor**

Add to `SleepFeatureExtractor.kt` (after `computeQuality`):

```kotlin
    fun extract(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
    ): SleepFeatures {
        val validIntervals = buildSleepIntervals(sleepRecords)
        val feedIntervals = buildBreastfeedIntervals(feedSessions)
        val metrics = computeMetrics(validIntervals)
        val quality = computeQuality(validIntervals, rawRecordCount = sleepRecords.size, metrics = metrics)
        return SleepFeatures(validIntervals, feedIntervals, metrics, quality)
    }
```

- [ ] **Step 5: Run full test suite for the feature package**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.feature.*"
```

Expected: BUILD SUCCESSFUL — all tests pass across `SleepIntervalTest`, `BreastfeedIntervalTest`, `SleepFeatureExtractorTest`

- [ ] **Step 6: Run full suite to catch regressions**

```bash
./gradlew :app:testDebugUnitTest -PfastTests
```

Expected: BUILD SUCCESSFUL — no regressions in breastfeeding, onboarding, repository, or other modules

- [ ] **Step 7: ktlintFormat + detekt**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: no violations

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatures.kt
git add app/src/main/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractor.kt
git add app/src/test/java/com/babytracker/domain/sleep/feature/SleepFeatureExtractorTest.kt
git commit -m "feat(sleep): add SleepFeatures and extract(), DST and sparse-logger tests [AKA-87]"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] `SleepInterval` — validates `end <= start`, naps > 4h, handles open records
- [x] `BreastfeedInterval` — validates, active signal via null end
- [x] `SleepMetrics` — all 11 fields: lastWake, lastSleepType, lastSleepDuration, completedWakeIntervals, medianWakeInterval, IQR, sleepLast24h, daySleepToday, napCountToday, medianBedtime, medianMorningWake
- [x] `EvidenceQuality` — freshness, interval count, localDayCoverage, IQR, invalidRate, passesGatingBar
- [x] Overlapping records rejected and counted in invalid rate
- [x] Cross-midnight `sleepLast24h` via epoch-delta interval overlap
- [x] `ZoneId` injected — never `ZoneId.systemDefault()`
- [x] Local-day features excluded from gating bar (Phase 0 timezone caveat)
- [x] Sparse-logger tests: one/day, nights-only, naps-only, stale last wake, overlapping records
- [x] DST tests: spring-forward, zone change
- [x] Zero Android imports in all production classes
- [x] `Clock` injected for deterministic tests

**Not in this issue (future AKA):**
- `SleepPredictionState` sealed type — AKA-88 (baseline predictor)
- `SleepWindow`, `Confidence` — AKA-88
- `PredictSleepWindowUseCase` — AKA-88
- Evaluation harness — AKA-91
- Fall-back DST test: similar pattern to spring-forward; add if desired but not required by the spec's listed acceptance criteria
