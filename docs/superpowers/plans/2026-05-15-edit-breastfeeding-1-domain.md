# Edit Past Breastfeeding Session — PR 1: Domain Layer

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add delete + update capabilities to the data/domain layer for breastfeeding sessions. No UI changes. Fully testable in isolation.

**Branch:** `feat/breastfeeding-edit-domain` (branch from `main`)

**Part of:** Edit Past Breastfeeding Session feature — see sibling plans:
- **PR 2:** `2026-05-15-edit-breastfeeding-2-viewmodel.md`
- **PR 3:** `2026-05-15-edit-breastfeeding-3-ui.md`

**Architecture:** Add `@Delete` to DAO. Extend `BreastfeedingRepository` interface + impl with `deleteSession`. Introduce three domain artifacts: `validateBreastfeedingEdit` (pure validation function), `UpdateBreastfeedingSessionUseCase`, `DeleteBreastfeedingSessionUseCase`.

**Tech Stack:** Kotlin 2.3.20, Room 2.8.4, Hilt 2.59, JUnit 5, MockK 1.13.13.

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/data/local/dao/BreastfeedingDao.kt` |
| Modify | `app/src/main/java/com/babytracker/domain/repository/BreastfeedingRepository.kt` |
| Modify | `app/src/main/java/com/babytracker/data/repository/BreastfeedingRepositoryImpl.kt` |
| Create | `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEdit.kt` |
| Create | `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEditTest.kt` |
| Create | `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCase.kt` |
| Create | `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCaseTest.kt` |
| Create | `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCase.kt` |
| Create | `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCaseTest.kt` |

---

## Task 1: Add Delete Capability to Repository

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/dao/BreastfeedingDao.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/BreastfeedingRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/BreastfeedingRepositoryImpl.kt`

- [ ] **Step 1: Add `@Delete` method to DAO**

Open `app/src/main/java/com/babytracker/data/local/dao/BreastfeedingDao.kt` and add the `Delete` import + the new function. Final file:

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.BreastfeedingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreastfeedingDao {
    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<BreastfeedingEntity>>

    @Query("SELECT * FROM breastfeeding_sessions WHERE end_time IS NULL LIMIT 1")
    fun getActiveSession(): Flow<BreastfeedingEntity?>

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC LIMIT 1")
    suspend fun getLastSession(): BreastfeedingEntity?

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<BreastfeedingEntity>

    @Insert
    suspend fun insertSession(entity: BreastfeedingEntity): Long

    @Update
    suspend fun updateSession(entity: BreastfeedingEntity)

    @Delete
    suspend fun deleteSession(entity: BreastfeedingEntity)
}
```

- [ ] **Step 2: Add `deleteSession` to repository interface**

Replace `app/src/main/java/com/babytracker/domain/repository/BreastfeedingRepository.kt`:

```kotlin
package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingSession
import kotlinx.coroutines.flow.Flow

interface BreastfeedingRepository {
    fun getAllSessions(): Flow<List<BreastfeedingSession>>
    fun getActiveSession(): Flow<BreastfeedingSession?>
    suspend fun getLastSession(): BreastfeedingSession?
    suspend fun getRecentSessions(limit: Int): List<BreastfeedingSession>
    suspend fun insertSession(session: BreastfeedingSession): Long
    suspend fun updateSession(session: BreastfeedingSession)
    suspend fun deleteSession(session: BreastfeedingSession)
}
```

- [ ] **Step 3: Implement `deleteSession` in `BreastfeedingRepositoryImpl`**

Replace `app/src/main/java/com/babytracker/data/repository/BreastfeedingRepositoryImpl.kt`:

```kotlin
package com.babytracker.data.repository

import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreastfeedingRepositoryImpl @Inject constructor(
    private val dao: BreastfeedingDao
) : BreastfeedingRepository {
    override fun getAllSessions(): Flow<List<BreastfeedingSession>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveSession(): Flow<BreastfeedingSession?> =
        dao.getActiveSession().map { it?.toDomain() }

    override suspend fun getLastSession(): BreastfeedingSession? =
        dao.getLastSession()?.toDomain()

    override suspend fun getRecentSessions(limit: Int): List<BreastfeedingSession> =
        dao.getRecentSessions(limit).map { it.toDomain() }

    override suspend fun insertSession(session: BreastfeedingSession): Long =
        dao.insertSession(session.toEntity())

    override suspend fun updateSession(session: BreastfeedingSession) =
        dao.updateSession(session.toEntity())

    override suspend fun deleteSession(session: BreastfeedingSession) =
        dao.deleteSession(session.toEntity())
}
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/dao/BreastfeedingDao.kt app/src/main/java/com/babytracker/domain/repository/BreastfeedingRepository.kt app/src/main/java/com/babytracker/data/repository/BreastfeedingRepositoryImpl.kt
git commit -m "feat(repository): add deleteSession to BreastfeedingRepository"
```

---

## Task 2: Edit Validation Function

A pure top-level function that returns `null` for valid edits and a user-facing error message string for invalid ones. No sealed wrapper (KISS, matches CLAUDE.md guidance). Lives in domain so the ViewModel and use case both share one source of truth.

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEdit.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEditTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEditTest.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidateBreastfeedingEditTest {

    private val now = Instant.parse("2026-05-15T10:00:00Z")
    private val start = Instant.parse("2026-05-15T09:00:00Z")

    @Test
    fun returnsNullWhenEndAfterStartAndPauseFits() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start.plusSeconds(1800),
            pausedDurationMs = 60_000L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenEndTimeIsNull() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsErrorWhenEndEqualsStart() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("End time must be after start time", result)
    }

    @Test
    fun returnsErrorWhenEndBeforeStart() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start.minusSeconds(60),
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("End time must be after start time", result)
    }

    @Test
    fun returnsErrorWhenEndInFuture() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = now.plusSeconds(60),
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("End time can't be in the future", result)
    }

    @Test
    fun returnsErrorWhenStartInFuture() {
        val result = validateBreastfeedingEdit(
            startTime = now.plusSeconds(60),
            endTime = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("Start time can't be in the future", result)
    }

    @Test
    fun returnsErrorWhenPausedDurationExceedsSessionLength() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start.plusSeconds(600),
            pausedDurationMs = 700_000L,
            now = now,
        )
        assertEquals("Session is shorter than recorded pauses", result)
    }

    @Test
    fun futureEndChecksBeforeStartOrderCheck() {
        // both wrong; future-end error wins so user sees the more relevant message
        val result = validateBreastfeedingEdit(
            startTime = now.plusSeconds(120),
            endTime = now.plusSeconds(60),
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("Start time can't be in the future", result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.ValidateBreastfeedingEditTest"`
Expected: FAIL with unresolved reference `validateBreastfeedingEdit`.

- [ ] **Step 3: Implement the validation function**

Create `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEdit.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import java.time.Duration
import java.time.Instant

/**
 * Returns `null` when the proposed edit is valid, otherwise a user-facing error message.
 * Order is intentional: future-time errors take priority over ordering errors so the user
 * sees the most actionable correction first.
 */
fun validateBreastfeedingEdit(
    startTime: Instant,
    endTime: Instant?,
    pausedDurationMs: Long,
    now: Instant,
): String? {
    if (startTime.isAfter(now)) return "Start time can't be in the future"
    if (endTime != null && endTime.isAfter(now)) return "End time can't be in the future"
    if (endTime != null && !endTime.isAfter(startTime)) return "End time must be after start time"
    if (endTime != null) {
        val sessionMs = Duration.between(startTime, endTime).toMillis()
        if (pausedDurationMs > sessionMs) return "Session is shorter than recorded pauses"
    }
    return null
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.ValidateBreastfeedingEditTest"`
Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEdit.kt app/src/test/java/com/babytracker/domain/usecase/breastfeeding/ValidateBreastfeedingEditTest.kt
git commit -m "feat(breastfeeding): add validateBreastfeedingEdit for edit-flow input checks"
```

---

## Task 3: UpdateBreastfeedingSessionUseCase

Applies edited start/end times to a session, clamps `switchTime` and `pausedAt` to the new range (nulling them when outside), and persists. Throws `IllegalArgumentException` for invalid input — the ViewModel guards by calling `validateBreastfeedingEdit` first.

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCaseTest.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class UpdateBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: UpdateBreastfeedingSessionUseCase

    private val now = Instant.parse("2026-05-15T12:00:00Z")
    private val originalStart = Instant.parse("2026-05-15T09:00:00Z")
    private val originalEnd = Instant.parse("2026-05-15T09:30:00Z")
    private val originalSwitch = Instant.parse("2026-05-15T09:15:00Z")

    private val session = BreastfeedingSession(
        id = 7L,
        startTime = originalStart,
        endTime = originalEnd,
        startingSide = BreastSide.LEFT,
        switchTime = originalSwitch,
        pausedAt = null,
        pausedDurationMs = 0L,
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = UpdateBreastfeedingSessionUseCase(repository) { now }
    }

    @Test
    fun invokeUpdatesStartAndEndTime() = runTest {
        val newStart = Instant.parse("2026-05-15T10:00:00Z")
        val newEnd = Instant.parse("2026-05-15T10:45:00Z")
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(session, newStart, newEnd)

        assertEquals(newStart, slot.captured.startTime)
        assertEquals(newEnd, slot.captured.endTime)
    }

    @Test
    fun invokePreservesIdAndStartingSide() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(
            session,
            newStart = Instant.parse("2026-05-15T10:00:00Z"),
            newEnd = Instant.parse("2026-05-15T10:45:00Z"),
        )

        assertEquals(7L, slot.captured.id)
        assertEquals(BreastSide.LEFT, slot.captured.startingSide)
    }

    @Test
    fun invokeKeepsSwitchTimeWhenInsideNewRange() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        // new range still contains originalSwitch (09:15)
        useCase(
            session,
            newStart = Instant.parse("2026-05-15T09:00:00Z"),
            newEnd = Instant.parse("2026-05-15T09:45:00Z"),
        )

        assertEquals(originalSwitch, slot.captured.switchTime)
    }

    @Test
    fun invokeNullsSwitchTimeWhenOutsideNewRange() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        // shift range past the original switch time
        useCase(
            session,
            newStart = Instant.parse("2026-05-15T09:20:00Z"),
            newEnd = Instant.parse("2026-05-15T09:50:00Z"),
        )

        assertNull(slot.captured.switchTime)
    }

    @Test
    fun invokeNullsPausedAtWhenOutsideNewRange() = runTest {
        val pausedSession = session.copy(
            pausedAt = Instant.parse("2026-05-15T09:10:00Z"),
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(
            pausedSession,
            newStart = Instant.parse("2026-05-15T09:20:00Z"),
            newEnd = Instant.parse("2026-05-15T09:50:00Z"),
        )

        assertNull(slot.captured.pausedAt)
    }

    @Test
    fun invokeAllowsNullEndForInProgressSession() = runTest {
        val inProgress = session.copy(endTime = null, switchTime = null)
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        val newStart = Instant.parse("2026-05-15T10:00:00Z")
        useCase(inProgress, newStart, null)

        assertEquals(newStart, slot.captured.startTime)
        assertNull(slot.captured.endTime)
    }

    @Test
    fun invokeThrowsWhenEndBeforeStart() = runTest {
        val newStart = Instant.parse("2026-05-15T10:00:00Z")
        val newEnd = Instant.parse("2026-05-15T09:30:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(session, newStart, newEnd) }
        }
    }

    @Test
    fun invokeThrowsWhenEndInFuture() = runTest {
        val newStart = Instant.parse("2026-05-15T11:00:00Z")
        val newEnd = now.plusSeconds(60)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(session, newStart, newEnd) }
        }
    }

    @Test
    fun invokeCallsUpdateExactlyOnceOnValidInput() = runTest {
        coJustRun { repository.updateSession(any()) }

        useCase(
            session,
            newStart = Instant.parse("2026-05-15T10:00:00Z"),
            newEnd = Instant.parse("2026-05-15T10:30:00Z"),
        )

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }

    @Test
    fun invokeClosingInProgressPausedSessionFoldsPauseIntoPausedDurationMs() = runTest {
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:20:00Z"),
            pausedDurationMs = 30_000L,
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        // Close 10 minutes after the pause began.
        val newEnd = Instant.parse("2026-05-15T09:30:00Z")
        useCase(inProgressPaused, inProgressPaused.startTime, newEnd)

        assertNull(slot.captured.pausedAt)
        assertEquals(30_000L + 10 * 60_000L, slot.captured.pausedDurationMs)
        assertEquals(newEnd, slot.captured.endTime)
    }

    @Test
    fun invokeClosingPausedSessionRejectsWhenPauseExceedsRange() = runTest {
        // Original paused at 09:20, original pausedDurationMs 0.
        // New range is 09:00 to 09:21 (1 minute total). Fold adds 60s of pause from 09:20 to 09:21.
        // sessionMs = 60_000 * 21 = 1_260_000, foldedPause = 60_000 — still valid.
        // But if we squeeze further so fold exceeds session, validation must reject.
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:00:30Z"),
            pausedDurationMs = 0L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    inProgressPaused,
                    newStart = Instant.parse("2026-05-15T09:00:00Z"),
                    newEnd = Instant.parse("2026-05-15T09:00:15Z"),
                )
            }
        }
    }

    @Test
    fun invokeStayingInProgressKeepsPausedAtWhenInRange() = runTest {
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:10:00Z"),
            pausedDurationMs = 5_000L,
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(inProgressPaused, newStart = inProgressPaused.startTime, newEnd = null)

        assertEquals(Instant.parse("2026-05-15T09:10:00Z"), slot.captured.pausedAt)
        assertEquals(5_000L, slot.captured.pausedDurationMs)
        assertNull(slot.captured.endTime)
    }

    @Test
    fun invokeStayingInProgressNullsPausedAtWhenOutsideNewRange() = runTest {
        val inProgressPaused = session.copy(
            endTime = null,
            switchTime = null,
            pausedAt = Instant.parse("2026-05-15T09:10:00Z"),
        )
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(slot)) }

        useCase(
            inProgressPaused,
            newStart = Instant.parse("2026-05-15T09:15:00Z"),
            newEnd = null,
        )

        assertNull(slot.captured.pausedAt)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCaseTest"`
Expected: FAIL with unresolved reference `UpdateBreastfeedingSessionUseCase`.

- [ ] **Step 3: Implement the use case**

Create `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class UpdateBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val nowProvider: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        session: BreastfeedingSession,
        newStart: Instant,
        newEnd: Instant?,
    ) {
        val (newPausedDurationMs, newPausedAt) = foldPause(session, newStart, newEnd)

        val error = validateBreastfeedingEdit(
            startTime = newStart,
            endTime = newEnd,
            pausedDurationMs = newPausedDurationMs,
            now = nowProvider(),
        )
        require(error == null) { error!! }

        val clampedSwitch = session.switchTime?.takeIf { sw ->
            !sw.isBefore(newStart) && (newEnd == null || !sw.isAfter(newEnd))
        }

        repository.updateSession(
            session.copy(
                startTime = newStart,
                endTime = newEnd,
                switchTime = clampedSwitch,
                pausedAt = newPausedAt,
                pausedDurationMs = newPausedDurationMs,
            )
        )
    }
}

/**
 * Returns the (pausedDurationMs, pausedAt) pair that should be persisted after an edit.
 *
 * When the edit closes an in-progress paused session (newEnd != null && session.pausedAt != null),
 * the pause interval between pausedAt (or newStart, whichever is later) and newEnd is folded into
 * pausedDurationMs, and pausedAt is cleared. Without this fold, the closed session would still be
 * marked paused and its computed active duration would silently include the trailing pause.
 *
 * When the edit keeps the session in-progress (newEnd == null), pausedAt is preserved only when it
 * still falls within the new range; pausedDurationMs is untouched.
 */
internal fun foldPause(
    session: BreastfeedingSession,
    newStart: Instant,
    newEnd: Instant?,
): Pair<Long, Instant?> {
    val pausedAt = session.pausedAt ?: return session.pausedDurationMs to null

    if (newEnd == null) {
        val keep = !pausedAt.isBefore(newStart)
        return session.pausedDurationMs to (if (keep) pausedAt else null)
    }

    val pauseStart = if (pausedAt.isBefore(newStart)) newStart else pausedAt
    if (!pauseStart.isBefore(newEnd)) {
        return session.pausedDurationMs to null
    }
    val extraMs = java.time.Duration.between(pauseStart, newEnd).toMillis()
    return (session.pausedDurationMs + extraMs) to null
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCaseTest"`
Expected: 13 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCase.kt app/src/test/java/com/babytracker/domain/usecase/breastfeeding/UpdateBreastfeedingSessionUseCaseTest.kt
git commit -m "feat(breastfeeding): add UpdateBreastfeedingSessionUseCase"
```

---

## Task 4: DeleteBreastfeedingSessionUseCase

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCaseTest.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DeleteBreastfeedingSessionUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: DeleteBreastfeedingSessionUseCase

    private val session = BreastfeedingSession(
        id = 13L,
        startTime = Instant.parse("2026-05-15T09:00:00Z"),
        endTime = Instant.parse("2026-05-15T09:30:00Z"),
        startingSide = BreastSide.RIGHT,
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = DeleteBreastfeedingSessionUseCase(repository)
    }

    @Test
    fun invokeCallsDeleteSessionOnce() = runTest {
        coJustRun { repository.deleteSession(any()) }

        useCase(session)

        coVerify(exactly = 1) { repository.deleteSession(any()) }
    }

    @Test
    fun invokePassesSameSessionToRepository() = runTest {
        val slot = slot<BreastfeedingSession>()
        coJustRun { repository.deleteSession(capture(slot)) }

        useCase(session)

        assertEquals(13L, slot.captured.id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.DeleteBreastfeedingSessionUseCaseTest"`
Expected: FAIL with unresolved reference `DeleteBreastfeedingSessionUseCase`.

- [ ] **Step 3: Implement the use case**

Create `app/src/main/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import javax.inject.Inject

class DeleteBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        repository.deleteSession(session)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.usecase.breastfeeding.DeleteBreastfeedingSessionUseCaseTest"`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCase.kt app/src/test/java/com/babytracker/domain/usecase/breastfeeding/DeleteBreastfeedingSessionUseCaseTest.kt
git commit -m "feat(breastfeeding): add DeleteBreastfeedingSessionUseCase"
```

---

## Final: Run Full Suite + Quality Checks + Open PR

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: ktlintFormat and detekt**

```
./gradlew ktlintFormat
./gradlew detekt
```
Expected: both succeed; fix violations by adjusting code (never `@Suppress`).

- [ ] **Step 3: Push and open PR**

```bash
git push -u origin feat/breastfeeding-edit-domain
```

Open PR targeting `main`. Title: `feat(breastfeeding): add delete/update domain layer for session editing`.
