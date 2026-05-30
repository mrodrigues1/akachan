LINEAR_ISSUE: AKA-63

# Quick Tile Toggle Handler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single-flight toggle handler that starts/stops feed and sleep sessions silently from Quick Settings when the tap policy allows it.

**Architecture:** Implement a singleton `TileToggleHandler` in `com.babytracker.tile` that serializes toggle bodies with a `Mutex`. It uses the atomic repository operations from Plan 2, side/type helpers from Plan 3, and preserves the side effects expected from existing app flows: breastfeeding notifications, sleep notifications, nap reminder scheduling, Firestore sync, and widget refresh. Post-mutation side effects are best-effort; failures must not mask a committed tracking mutation, so the handler still returns `CHANGED` after the data write succeeds.

**Tech Stack:** Kotlin, Coroutines, `Mutex`, `Clock`, Hilt singleton, MockK, JUnit 5.

---

## Dependencies

- Depends on Plan 2 / `AKA-61` for `startSessionIfNone`, `stopActiveSession`, `startRecordIfNone`, and `stopActiveRecord`.
- Depends on Plan 3 / `AKA-62` for `alternateSide` and `sleepTypeFor`.

## Suggested Branch

`feat/quick-tile-toggle-handler`

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/babytracker/tile/TileToggleHandler.kt` | Feed/sleep toggle orchestration, side effects, and single-flight mutex. | Create |
| `app/src/main/java/com/babytracker/di/ClockModule.kt` | Hilt binding for `Clock.systemDefaultZone()`. | Create |
| `app/src/test/java/com/babytracker/tile/TileToggleHandlerTest.kt` | Unit tests for start/stop/no-op/error/mutex/side-effect behavior. | Create |

## Implementation Tasks

### Task 1: Define handler result

- [ ] In `TileToggleHandler.kt`, define:

```kotlin
package com.babytracker.tile

enum class TileToggleResult {
    CHANGED,
    NO_OP,
    FAILED,
}
```

### Task 2: Add Clock binding

- [ ] Create `app/src/main/java/com/babytracker/di/ClockModule.kt`:

```kotlin
package com.babytracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
```

- [ ] Tests construct `TileToggleHandler` directly with a fixed `Clock`.

### Task 3: Implement handler constructor and feed toggle

- [ ] Create `TileToggleHandler` with explicit dependencies:

```kotlin
@Singleton
class TileToggleHandler @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val breastfeedingNotifications: BreastfeedingSessionNotificationCoordinator,
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val napReminderScheduler: NapReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val widgetUpdater: GlanceWidgetUpdater,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun toggleFeed(): TileToggleResult = mutex.withLock {
        runCatching {
            val now = clock.instant()
            val active = breastfeedingRepository.getActiveSession().first()
            val changed = if (active != null) {
                val stopped = breastfeedingRepository.stopActiveSession(now)
                if (stopped) {
                    breastfeedingNotifications.cancelScheduled()
                    breastfeedingNotifications.cancelPostedSessionNotifications()
                    runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
                }
                stopped
            } else {
                val side = alternateSide(breastfeedingRepository.getLastSession())
                val session = BreastfeedingSession(startTime = now, startingSide = side)
                val id = breastfeedingRepository.startSessionIfNone(session)
                if (id != null) {
                    val created = session.copy(id = id)
                    runCatching { breastfeedingNotifications.scheduleInitial(created) }
                    runCatching { breastfeedingNotifications.showRunning(created) }
                    runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
                }
                id != null
            }
            if (changed) runCatching { widgetUpdater.updateAll() }
            if (changed) TileToggleResult.CHANGED else TileToggleResult.NO_OP
        }.getOrElse {
            TileToggleResult.FAILED
        }
    }
}
```

- [ ] Import `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.sync.Mutex`, and `kotlinx.coroutines.sync.withLock`.
- [ ] Do not call existing start/stop use cases; they use non-atomic insert/update paths. Preserve their surrounding side effects explicitly and cover them with tests.

### Task 4: Add sleep toggle

- [ ] Add:

```kotlin
suspend fun toggleSleep(): TileToggleResult = mutex.withLock {
    runCatching {
        val now = clock.instant()
        val latest = sleepRepository.getLatestRecord()
        val changed = if (latest?.isInProgress == true) {
            val stopped = sleepRepository.stopActiveRecord(now)
            if (stopped) {
                sleepNotificationScheduler.cancel()
                if (latest.sleepType == SleepType.NAP) {
                    val enabled = settingsRepository.getNapReminderEnabled().first()
                    if (enabled) {
                        val delayMinutes = settingsRepository.getNapReminderDelayMinutes().first()
                        napReminderScheduler.schedule(now, delayMinutes)
                    }
                }
                runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
            }
            stopped
        } else {
            val record = SleepRecord(
                startTime = now,
                sleepType = sleepTypeFor(now, clock.zone),
            )
            val id = sleepRepository.startRecordIfNone(record)
            if (id != null) {
                val created = record.copy(id = id)
                runCatching { napReminderScheduler.cancel() }
                runCatching { sleepNotificationScheduler.show(created.id, created.sleepType, created.startTime) }
                runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
            }
            id != null
        }
        if (changed) runCatching { widgetUpdater.updateAll() }
        if (changed) TileToggleResult.CHANGED else TileToggleResult.NO_OP
    }.getOrElse {
        TileToggleResult.FAILED
    }
}
```

### Task 5: Add feed tests

- [ ] Create `TileToggleHandlerTest.kt`.
- [ ] Test feed stop branch: active session exists, `stopActiveSession(now)` returns true, result is `CHANGED`, scheduled and posted breastfeeding notifications are cancelled, Firestore session sync is requested, and widget update is called once.
- [ ] Test feed start branch: no active session, last completed session switched/final side handled by Plan 3 helper, `startSessionIfNone` receives the expected starting side and returns id, result is `CHANGED`, initial breastfeeding notifications are scheduled/shown, Firestore session sync is requested, and widget update is called once.
- [ ] Test feed no-op: no active session, `startSessionIfNone` returns null, result is `NO_OP`, notifications/sync/widget are not called.
- [ ] Test feed failure: repository throws before mutation, result is `FAILED`, side effects are not called.
- [ ] Test feed widget-refresh failure after a successful mutation: result is still `CHANGED`.

### Task 6: Add sleep tests

- [ ] Test sleep stop branch for `NAP`: latest record is in progress, `stopActiveRecord(now)` returns true, result is `CHANGED`, sleep notification is cancelled, nap reminder is scheduled when enabled, Firestore sleep sync is requested, and widget update is called once.
- [ ] Test sleep stop branch for `NIGHT_SLEEP`: sleep notification is cancelled but nap reminder is not scheduled.
- [ ] Test sleep start branch at 19:00 local time: `startRecordIfNone` receives `SleepType.NIGHT_SLEEP`, nap reminder is cancelled, sleep notification is shown, Firestore sleep sync is requested.
- [ ] Test sleep start branch at midday local time: receives `SleepType.NAP`.
- [ ] Test sleep no-op: `startRecordIfNone` returns null, result is `NO_OP`, notifications/sync/widget are not called.
- [ ] Test sleep failure: repository throws before mutation, result is `FAILED`, side effects are not called.
- [ ] Test sleep widget-refresh failure after a successful mutation: result is still `CHANGED`.

### Task 7: Add mutex serialization test

- [ ] Add a coroutine test that launches two concurrent `toggleFeed()` calls while the first repository call is suspended.
- [ ] Assert the second call does not enter repository work until the first completes.
- [ ] Keep the assertion behavioral: use a `CompletableDeferred` gate and a call-order list instead of sleeping.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.tile.TileToggleHandlerTest" -PfastTests
```

Expected after implementation: pass.

## Acceptance Criteria

- Feed and sleep toggles return `CHANGED`, `NO_OP`, or `FAILED` without throwing for expected repository races.
- Feed starts use the Plan 3 side alternation helper.
- Sleep starts use the Plan 3 time-of-day helper.
- Successful mutations preserve notification and Firestore sync side effects from existing app flows.
- Widget refresh runs only after a state change.
- Widget refresh failure after a successful mutation does not change the result from `CHANGED` to `FAILED`.
- `Clock` has an explicit Hilt binding, so constructor injection compiles.
- A single `Mutex` serializes feed/sleep tile toggles in-process.
- Targeted handler tests pass.
