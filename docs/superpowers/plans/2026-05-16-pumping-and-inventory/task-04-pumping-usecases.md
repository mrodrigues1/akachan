# Task 4 — Pumping use cases

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Ship every business operation the pumping ViewModels need.

**Depends on:** Task 3 (PumpingRepository).

## Files (all under `app/src/main/java/com/babytracker/domain/usecase/pumping/`)

- Create: `StartPumpingSessionUseCase.kt`
- Create: `StopPumpingSessionUseCase.kt`
- Create: `PausePumpingSessionUseCase.kt`
- Create: `ResumePumpingSessionUseCase.kt`
- Create: `SavePumpingSessionUseCase.kt`
- Create: `UpdatePumpingSessionUseCase.kt`
- Create: `DeletePumpingSessionUseCase.kt`
- Create: `GetPumpingHistoryUseCase.kt`
- Create: `ValidatePumpingEdit.kt`
- Tests under `app/src/test/java/com/babytracker/domain/usecase/pumping/`

## Implementation

### `StartPumpingSessionUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class StartPumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(breast: PumpingBreast): Long {
        val session = PumpingSession(startTime = now(), breast = breast)
        return repository.insert(session)
    }
}
```

> Inject `() -> Instant` from `DatabaseModule.provideNowProvider()` so tests can pass a fixed clock.

### `StopPumpingSessionUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class StopPumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: PumpingSession, volumeMl: Int?): PumpingSession {
        require(session.isInProgress) { "Cannot stop a completed session" }
        if (volumeMl != null) require(volumeMl > 0) { "Volume must be greater than 0" }
        val endInstant = now()
        val resolvedPausedMs = if (session.isPaused) {
            session.pausedDurationMs +
                (endInstant.toEpochMilli() - session.pausedAt!!.toEpochMilli()).coerceAtLeast(0L)
        } else {
            session.pausedDurationMs
        }
        val updated = session.copy(
            endTime = endInstant,
            volumeMl = volumeMl,
            pausedAt = null,
            pausedDurationMs = resolvedPausedMs,
        )
        repository.update(updated)
        return updated
    }
}
```

### `PausePumpingSessionUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class PausePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: PumpingSession) {
        require(session.isInProgress) { "Cannot pause a completed session" }
        if (session.isPaused) return
        repository.update(session.copy(pausedAt = now()))
    }
}
```

### `ResumePumpingSessionUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class ResumePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: PumpingSession) {
        require(session.isInProgress) { "Cannot resume a completed session" }
        val pausedAt = session.pausedAt ?: return
        val addedMs = (now().toEpochMilli() - pausedAt.toEpochMilli()).coerceAtLeast(0L)
        repository.update(
            session.copy(
                pausedAt = null,
                pausedDurationMs = session.pausedDurationMs + addedMs,
            )
        )
    }
}
```

### `SavePumpingSessionUseCase.kt` (manual entry)

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class SavePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        breast: PumpingBreast,
        volumeMl: Int,
        notes: String?,
    ): PumpingSession {
        require(endTime.isAfter(startTime)) { "End must be after start" }
        require(volumeMl > 0) { "Volume must be greater than 0" }
        val session = PumpingSession(
            startTime = startTime,
            endTime = endTime,
            breast = breast,
            volumeMl = volumeMl,
            notes = notes,
        )
        val id = repository.insert(session)
        return session.copy(id = id)
    }
}
```

### `UpdatePumpingSessionUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class UpdatePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    suspend operator fun invoke(
        original: PumpingSession,
        startTime: Instant,
        endTime: Instant?,
        breast: PumpingBreast,
        volumeMl: Int?,
        notes: String?,
    ): PumpingSession {
        if (endTime != null) require(endTime.isAfter(startTime)) { "End must be after start" }
        if (volumeMl != null) require(volumeMl > 0) { "Volume must be greater than 0" }
        val updated = original.copy(
            startTime = startTime,
            endTime = endTime,
            breast = breast,
            volumeMl = volumeMl,
            notes = notes,
        )
        repository.update(updated)
        return updated
    }
}
```

### `DeletePumpingSessionUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import javax.inject.Inject

class DeletePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    suspend operator fun invoke(session: PumpingSession) = repository.delete(session)
}
```

### `GetPumpingHistoryUseCase.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPumpingHistoryUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    operator fun invoke(): Flow<List<PumpingSession>> = repository.getAllSessions()
}
```

### `ValidatePumpingEdit.kt`

```kotlin
package com.babytracker.domain.usecase.pumping

import java.time.Instant

fun validatePumpingEdit(
    startTime: Instant,
    endTime: Instant?,
    volumeMl: Int?,
    pausedDurationMs: Long,
    now: Instant,
): String? {
    if (endTime != null && !endTime.isAfter(startTime)) return "End must be after start"
    if (endTime != null && endTime.isAfter(now)) return "End cannot be in the future"
    if (endTime != null) {
        val active = endTime.toEpochMilli() - startTime.toEpochMilli() - pausedDurationMs
        if (active < 0) return "Paused time exceeds session length"
    }
    if (volumeMl != null && volumeMl <= 0) return "Volume must be greater than 0"
    return null
}
```

## Tests

Mirror the breastfeeding use-case test style (MockK + JUnit 5 + Turbine). Test classes:

- `StartPumpingSessionUseCaseTest` — verifies `now()` flows into `repository.insert`.
- `StopPumpingSessionUseCaseTest` — completed session throws; volume validation; resolved `pausedDurationMs` when stopped while paused.
- `PausePumpingSessionUseCaseTest` — sets `pausedAt`; no-op if already paused; throws on completed.
- `ResumePumpingSessionUseCaseTest` — clears `pausedAt`, accumulates paused ms.
- `SavePumpingSessionUseCaseTest` — manual entry persists; rejects `endTime <= startTime`; rejects non-positive volume.
- `UpdatePumpingSessionUseCaseTest` — happy path; rejects bad ranges/volume.
- `DeletePumpingSessionUseCaseTest` — forwards to repository.
- `GetPumpingHistoryUseCaseTest` — returns the repository flow unchanged (Turbine asserts emission shape).
- `ValidatePumpingEditTest` — covers each branch: end-before-start, end-in-future, paused-overflow, bad volume, valid case returns null.

Reference test (one example — `StopPumpingSessionUseCaseTest`):

```kotlin
package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class StopPumpingSessionUseCaseTest {
    private lateinit var repository: PumpingRepository
    private lateinit var useCase: StopPumpingSessionUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:30:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = StopPumpingSessionUseCase(repository) { fixedNow }
    }

    @Test
    fun `throws when session already completed`() = runTest {
        val completed = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            endTime = Instant.parse("2026-05-16T10:15:00Z"),
            breast = PumpingBreast.LEFT,
        )
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(completed, volumeMl = 100) }
        }
    }

    @Test
    fun `rejects non-positive volume`() = runTest {
        val active = PumpingSession(
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(active, volumeMl = 0) }
        }
    }

    @Test
    fun `folds paused window into pausedDurationMs when stopping while paused`() = runTest {
        val start = Instant.parse("2026-05-16T10:00:00Z")
        val pausedAt = Instant.parse("2026-05-16T10:20:00Z")
        val active = PumpingSession(
            startTime = start,
            breast = PumpingBreast.LEFT,
            pausedAt = pausedAt,
            pausedDurationMs = 0,
        )
        val result = useCase(active, volumeMl = 90)
        assertEquals(fixedNow, result.endTime)
        assertEquals(90, result.volumeMl)
        assertNull(result.pausedAt)
        assertEquals(10 * 60 * 1000L, result.pausedDurationMs) // 10 minutes paused
        coVerify { repository.update(result) }
    }
}
```

Apply the same pattern to the other test files. Keep them small and deterministic.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.domain.usecase.pumping.*"
```

Expected: all green.

## Commit

```
feat(pumping): add pumping use cases (timer, manual, edit, validate)

Introduces start/stop/pause/resume/save/update/delete/getHistory use cases
plus validatePumpingEdit helper. Mirrors the breastfeeding pattern.
```
