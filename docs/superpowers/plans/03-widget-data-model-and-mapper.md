# WidgetData Model + Mapper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** [AKA-50](https://linear.app/akachan/issue/AKA-50/widgetdata-model-towidgetdata-mapper)
**Spec:** `docs/superpowers/specs/2026-05-24-home-screen-widget-design.md` (Data read section)
**Suggested branch:** `feat/widget-data-model-and-mapper`
**Depends on:** Plan 2 (`SleepRepository.getLatestRecord`)
**Blocks:** Plan 5 (widget content composables read this type)

**Goal:** Introduce the `WidgetData` plain-Kotlin type the Glance composables will render from, plus a pure mapper `toWidgetData(...)` that converts a `BreastfeedingSession?`, a `SleepRecord?`, and the configured baby name into the right sleep state (SLEEPING / AWAKE / NONE) and feed fields. Mapper is pure (no Android, no Hilt, no DAO) so it is fully covered by JVM unit tests.

**Architecture:** New `widget/data/` sub-package keeps the model close to the widget code that consumes it while honoring CLAUDE.md's anti-Mapper rule — the conversion lives as a top-level function, not a class. Sleep state is derived from the single latest record: open → SLEEPING (`sleepSince = startTime`); completed → AWAKE (`sleepSince = endTime`); null → NONE. Feed fields come straight from `BreastfeedingRepository.getLastSession()` and are nullable when no session exists. The function is the only place where domain → widget mapping logic lives; widget composables stay framework-light.

**Tech Stack:** Pure Kotlin, JUnit 5, MockK is not required (mapper takes plain inputs).

---

## Files

- Create: `app/src/main/java/com/babytracker/widget/data/WidgetData.kt`
- Create: `app/src/test/java/com/babytracker/widget/data/WidgetDataMapperTest.kt`

No interfaces, no DI module — `toWidgetData` is a top-level pure function.

---

### Task 1: Define the `WidgetData` model + `SleepState` enum

**Files:**
- Create: `app/src/main/java/com/babytracker/widget/data/WidgetData.kt`

- [ ] **Step 1: Write the model + enum**

```kotlin
package com.babytracker.widget.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import java.time.Instant

enum class SleepState {
    SLEEPING,
    AWAKE,
    NONE,
}

data class WidgetData(
    val babyName: String,
    val lastFeedSide: BreastSide?,
    val lastFeedStart: Instant?,
    val sleepState: SleepState,
    val sleepSince: Instant?,
) {
    companion object {
        val EMPTY: WidgetData = WidgetData(
            babyName = "Baby",
            lastFeedSide = null,
            lastFeedStart = null,
            sleepState = SleepState.NONE,
            sleepSince = null,
        )
    }
}
```

Invariants encoded in the type (enforced by the mapper, not by `init` checks — keep the model trivial):
- `sleepState == NONE` ⇒ `sleepSince == null`.
- `sleepState == SLEEPING` ⇒ `sleepSince` = sleep `startTime` (record is in-progress).
- `sleepState == AWAKE` ⇒ `sleepSince` = sleep `endTime` (non-null on the source record).
- `lastFeedSide == null` ⇔ `lastFeedStart == null` (no feed yet).

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Add the failing mapper test

**Files:**
- Create: `app/src/test/java/com/babytracker/widget/data/WidgetDataMapperTest.kt`

The mapper does not exist yet; the file references `toWidgetData`, so compilation will fail. That is the desired failing-test state.

- [ ] **Step 1: Write the test file**

```kotlin
package com.babytracker.widget.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WidgetDataMapperTest {

    private val babyName = "Akira"

    private fun feed(
        start: Instant = Instant.parse("2026-05-24T10:00:00Z"),
        end: Instant? = Instant.parse("2026-05-24T10:15:00Z"),
        side: BreastSide = BreastSide.LEFT,
    ) = BreastfeedingSession(
        id = 1,
        startTime = start,
        endTime = end,
        startingSide = side,
    )

    private fun sleep(
        start: Instant = Instant.parse("2026-05-24T11:00:00Z"),
        end: Instant? = null,
        type: SleepType = SleepType.NAP,
    ) = SleepRecord(
        id = 1,
        startTime = start,
        endTime = end,
        sleepType = type,
    )

    @Test
    fun `null name falls back to "Baby"`() {
        val result = toWidgetData(
            babyName = null,
            lastFeed = null,
            latestSleep = null,
        )

        assertEquals("Baby", result.babyName)
    }

    @Test
    fun `blank name falls back to "Baby"`() {
        val result = toWidgetData(
            babyName = "   ",
            lastFeed = null,
            latestSleep = null,
        )

        assertEquals("Baby", result.babyName)
    }

    @Test
    fun `name is used when non-blank`() {
        val result = toWidgetData(
            babyName = babyName,
            lastFeed = null,
            latestSleep = null,
        )

        assertEquals(babyName, result.babyName)
    }

    @Test
    fun `no feed produces null side and start`() {
        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = null)

        assertNull(result.lastFeedSide)
        assertNull(result.lastFeedStart)
    }

    @Test
    fun `completed feed copies side and startTime`() {
        val session = feed(
            start = Instant.parse("2026-05-24T08:00:00Z"),
            end = Instant.parse("2026-05-24T08:20:00Z"),
            side = BreastSide.RIGHT,
        )

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = null)

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
        assertEquals(Instant.parse("2026-05-24T08:00:00Z"), result.lastFeedStart)
    }

    @Test
    fun `in-progress feed still uses startTime as last feed time`() {
        val session = feed(
            start = Instant.parse("2026-05-24T09:00:00Z"),
            end = null,
            side = BreastSide.LEFT,
        )

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = null)

        assertEquals(BreastSide.LEFT, result.lastFeedSide)
        assertEquals(Instant.parse("2026-05-24T09:00:00Z"), result.lastFeedStart)
    }

    @Test
    fun `null sleep maps to NONE state`() {
        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = null)

        assertEquals(SleepState.NONE, result.sleepState)
        assertNull(result.sleepSince)
    }

    @Test
    fun `in-progress sleep maps to SLEEPING with sleepSince = startTime`() {
        val record = sleep(
            start = Instant.parse("2026-05-24T11:00:00Z"),
            end = null,
        )

        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = record)

        assertEquals(SleepState.SLEEPING, result.sleepState)
        assertEquals(Instant.parse("2026-05-24T11:00:00Z"), result.sleepSince)
    }

    @Test
    fun `completed sleep maps to AWAKE with sleepSince = endTime`() {
        val record = sleep(
            start = Instant.parse("2026-05-24T11:00:00Z"),
            end = Instant.parse("2026-05-24T12:00:00Z"),
        )

        val result = toWidgetData(babyName = babyName, lastFeed = null, latestSleep = record)

        assertEquals(SleepState.AWAKE, result.sleepState)
        assertEquals(Instant.parse("2026-05-24T12:00:00Z"), result.sleepSince)
    }

    @Test
    fun `combined feed plus in-progress sleep populates both rows`() {
        val session = feed(side = BreastSide.RIGHT)
        val record = sleep(end = null)

        val result = toWidgetData(babyName = babyName, lastFeed = session, latestSleep = record)

        assertEquals(BreastSide.RIGHT, result.lastFeedSide)
        assertEquals(SleepState.SLEEPING, result.sleepState)
    }
}
```

- [ ] **Step 2: Verify the test does not compile yet**

Run: `./gradlew :app:compileDebugUnitTestKotlin -q`
Expected: FAIL — `Unresolved reference: toWidgetData`. Continue to Task 3.

---

### Task 3: Implement the mapper

**Files:**
- Modify: `app/src/main/java/com/babytracker/widget/data/WidgetData.kt`

- [ ] **Step 1: Append the top-level function to the same file**

```kotlin
fun toWidgetData(
    babyName: String?,
    lastFeed: BreastfeedingSession?,
    latestSleep: SleepRecord?,
): WidgetData {
    val resolvedName = babyName?.takeIf { it.isNotBlank() } ?: "Baby"

    val sleepState = when {
        latestSleep == null -> SleepState.NONE
        latestSleep.endTime == null -> SleepState.SLEEPING
        else -> SleepState.AWAKE
    }
    val sleepSince: Instant? = when (sleepState) {
        SleepState.SLEEPING -> latestSleep?.startTime
        SleepState.AWAKE -> latestSleep?.endTime
        SleepState.NONE -> null
    }

    return WidgetData(
        babyName = resolvedName,
        lastFeedSide = lastFeed?.startingSide,
        lastFeedStart = lastFeed?.startTime,
        sleepState = sleepState,
        sleepSince = sleepSince,
    )
}
```

The function lives alongside the model in the same file (single-responsibility file: "what the widget renders + how to build it from domain types"). Keeping it top-level avoids an unnecessary class — consistent with CLAUDE.md ("no Mapper classes — extension functions or top-level fns").

- [ ] **Step 2: Run the new tests**

Run:
```
./gradlew :app:testDebugUnitTest --tests "com.babytracker.widget.data.WidgetDataMapperTest" -PfastTests
```
Expected: 10 tests PASS, 0 FAIL.

- [ ] **Step 3: Run the full unit suite**

Run:
```
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL. The Konsist architecture tests must still pass — `widget/` is a new package; if any arch rule (e.g. "every package owns at least one test") trips, add `@Tag("architecture")` and use the shared `productionScope` per CLAUDE.md.

---

### Task 4: Lint + static analysis

- [ ] **Step 1: Run**

```
./gradlew ktlintFormat detekt
```
Expected: BUILD SUCCESSFUL. No detekt findings on the new file.

---

### Task 5: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/com/babytracker/widget/data/WidgetData.kt \
        app/src/test/java/com/babytracker/widget/data/WidgetDataMapperTest.kt

git commit -m "feat(widget): add WidgetData model and toWidgetData mapper"
```

- [ ] **Step 2: Verify commit content**

Run: `git show --stat HEAD`
Expected: 2 files changed; no unrelated changes.

---

## Acceptance Criteria

- `WidgetData`, `SleepState`, and `toWidgetData(...)` all live in `app/src/main/java/com/babytracker/widget/data/WidgetData.kt`.
- The mapper is a pure top-level function and uses no Android or framework imports.
- Tests cover: null/blank/non-blank baby name; null / completed / in-progress feed; null / in-progress / completed sleep; combined feed+sleep.
- `./gradlew :app:testDebugUnitTest` and `./gradlew ktlintFormat detekt` succeed.
- One commit on `feat/plans-home-screen-widget`.
