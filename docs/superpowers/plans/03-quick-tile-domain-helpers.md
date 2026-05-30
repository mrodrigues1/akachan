LINEAR_ISSUE: AKA-62

# Quick Tile Domain Helpers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the pure, JVM-testable decision logic behind the Quick Settings tiles: side selection, sleep type selection, lockscreen tap policy, and render-state resolution.

**Architecture:** Create a small `com.babytracker.tile` package with data classes and pure helpers. Keep Android framework types out of the helpers so behavior is covered by fast unit tests and the later `TileService` classes stay thin. The resolver reads repositories, maps app/onboarding/session state into `TileRenderState`, and formats elapsed subtitles without depending on `Tile`.

**Tech Stack:** Kotlin, Coroutines, `Clock`, `ZoneId`, JUnit 5, MockK, existing repository interfaces.

---

## Dependencies

None for side/type/policy helpers. `TileStateResolver` uses existing repository APIs and can land before atomic toggle operations.

## Suggested Branch

`feat/quick-tile-domain-helpers`

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/babytracker/tile/TileRenderState.kt` | Render model and active/inactive/unavailable enum. | Create |
| `app/src/main/java/com/babytracker/tile/TileSideAndType.kt` | `alternateSide` and `sleepTypeFor` pure helpers. | Create |
| `app/src/main/java/com/babytracker/tile/TileTapPolicy.kt` | Pure lockscreen/unavailable tap decision. | Create |
| `app/src/main/java/com/babytracker/tile/TileStateResolver.kt` | Repository-backed feed/sleep state resolution. | Create |
| `app/src/test/java/com/babytracker/tile/TileSideAndTypeTest.kt` | Boundary tests for side/type helpers. | Create |
| `app/src/test/java/com/babytracker/tile/TileTapPolicyTest.kt` | Locked/unlocked/unavailable matrix. | Create |
| `app/src/test/java/com/babytracker/tile/TileStateResolverTest.kt` | App mode/onboarding/session render matrix. | Create |

## Implementation Tasks

### Task 1: Add render model

- [ ] Create `TileRenderState.kt`:

```kotlin
package com.babytracker.tile

enum class TileAvailability {
    ACTIVE,
    INACTIVE,
    UNAVAILABLE,
}

data class TileRenderState(
    val availability: TileAvailability,
    val label: String,
    val subtitle: String?,
)
```

### Task 2: Add and test side/type helpers

- [ ] Create `TileSideAndType.kt` with:

```kotlin
package com.babytracker.tile

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

fun alternateSide(last: BreastfeedingSession?): BreastSide =
    when (last?.finalSide()) {
        BreastSide.LEFT -> BreastSide.RIGHT
        BreastSide.RIGHT -> BreastSide.LEFT
        null -> BreastSide.LEFT
    }

fun BreastfeedingSession.finalSide(): BreastSide =
    if (switchTime == null) {
        startingSide
    } else {
        when (startingSide) {
            BreastSide.LEFT -> BreastSide.RIGHT
            BreastSide.RIGHT -> BreastSide.LEFT
        }
    }

fun sleepTypeFor(now: Instant, zoneId: ZoneId): SleepType {
    val local = now.atZone(zoneId).toLocalTime()
    return if (local >= LocalTime.of(19, 0) || local < LocalTime.of(6, 0)) {
        SleepType.NIGHT_SLEEP
    } else {
        SleepType.NAP
    }
}
```

- [ ] Add `TileSideAndTypeTest` covering:
  - no last session -> LEFT;
  - unswitched last session starting LEFT -> RIGHT;
  - unswitched last session starting RIGHT -> LEFT;
  - switched last session starting LEFT -> final side RIGHT -> next LEFT;
  - switched last session starting RIGHT -> final side LEFT -> next RIGHT;
  - sleep boundaries 18:59, 19:00, 02:00, 06:00, midday.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.tile.TileSideAndTypeTest" -PfastTests
```

Expected after implementation: pass.

### Task 3: Add tap policy helper

- [ ] Create `TileTapPolicy.kt`:

```kotlin
package com.babytracker.tile

enum class TileTapAction {
    OPEN_APP,
    TOGGLE_SILENTLY,
}

fun shouldOpenAppOnTap(
    isSecure: Boolean,
    isLocked: Boolean,
    state: TileAvailability,
): TileTapAction =
    if (state == TileAvailability.UNAVAILABLE || (isSecure && isLocked)) {
        TileTapAction.OPEN_APP
    } else {
        TileTapAction.TOGGLE_SILENTLY
    }
```

- [ ] Add `TileTapPolicyTest` covering secure+locked -> open app, secure+unlocked -> toggle, not-secure+locked -> toggle, unavailable -> open app regardless of lock state.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.tile.TileTapPolicyTest" -PfastTests
```

Expected after implementation: pass.

### Task 4: Implement state resolver

- [ ] Create `TileStateResolver.kt` with constructor dependencies:

```kotlin
class TileStateResolver(
    private val babyRepository: BabyRepository,
    private val settingsRepository: SettingsRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
)
```

- [ ] Add `resolveFeed()`:
  - If onboarding is not complete or app mode is `PARTNER`, return `UNAVAILABLE`, label `Feed`, subtitle `Open app`.
  - Treat onboarded `AppMode.NONE` the same as `PRIMARY`; this is the normal local-only mode and must support tile toggles.
  - If a breastfeeding session is active, return `ACTIVE`, label `Stop Feed`, subtitle elapsed since `startTime`.
  - If inactive, return `INACTIVE`, label `Start Feed`, subtitle `null`.
- [ ] Add `resolveSleep()` with the same availability logic:
  - active latest sleep record (`isInProgress`) -> `ACTIVE`, label `Stop Sleep`, elapsed subtitle;
  - inactive -> `INACTIVE`, label `Start Sleep`.
- [ ] Keep elapsed formatting deterministic and compact: `<hours>h <minutes>m` when at least one hour, otherwise `<minutes>m`; clamp negative durations to `0m`.
- [ ] Wrap repository reads in `runCatching`; on any failure, return `UNAVAILABLE` with the base tile label and `Open app`.

### Task 5: Test resolver matrix

- [ ] Add `TileStateResolverTest` covering:
  - app mode `NONE` with onboarding complete -> active/inactive states are available for both tiles;
  - app mode `PARTNER` -> unavailable for both tiles;
  - onboarding incomplete -> unavailable;
  - primary/onboarded/no active feed -> inactive `Start Feed`;
  - primary/onboarded/active feed -> active `Stop Feed` with elapsed subtitle;
  - primary/onboarded/no in-progress sleep -> inactive `Start Sleep`;
  - primary/onboarded/in-progress sleep -> active `Stop Sleep` with elapsed subtitle;
  - repository exception -> unavailable.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.tile.TileStateResolverTest" -PfastTests
```

Expected after implementation: pass.

## Acceptance Criteria

- Side alternation defaults to LEFT and alternates completed-session side.
- Switched breastfeeding sessions alternate from their final side, not blindly from their starting side.
- Sleep type heuristic matches 19:00-05:59 night sleep and 06:00-18:59 nap.
- Secure+locked taps route to app; unlocked and non-secure locked taps may toggle silently.
- Resolver returns deterministic active/inactive/unavailable render states for feed and sleep, with onboarded `NONE` and `PRIMARY` both available and `PARTNER` unavailable.
- All helper/resolver tests pass in the fast unit-test loop.
