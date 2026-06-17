# Diaper Use Cases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-160

**Goal:** Add the domain use cases for logging, editing, deleting, and observing diaper changes, plus a today-summary use case that feeds the home tile.

**Architecture:** Single-responsibility use cases with `suspend operator fun invoke(...)` (or `operator fun invoke()` for the Flow observers), each depending only on the `DiaperRepository` interface. Mirrors the `bottlefeed` use-case package. Time/zone come from the existing injected `() -> Instant` and `ZoneId` providers. **No Firestore sync here** — partner sync is added to these use cases in plan 5 (it needs `SyncToFirestoreUseCase.SyncType.DIAPERS`, introduced there).

**Tech Stack:** Kotlin, Coroutines/Flow, Hilt, JUnit 5 + MockK + Turbine.

**Dependencies:** Plan 1 (`feat/diaper-data-layer`) — needs `DiaperRepository`, `DiaperChange`, `DiaperType`.

**Suggested implementation branch:** `feat/diaper-use-cases`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `LogDiaperChangeUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/diaper/LogDiaperChangeUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/diaper/LogDiaperChangeUseCaseTest.kt`

- [ ] **Step 1: Implement** (mirrors `LogBottleFeedUseCase`; stamps `createdAt = now()`, rejects future timestamps)

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import java.time.Instant
import javax.inject.Inject

class LogDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        type: DiaperType,
        timestamp: Instant,
        notes: String? = null,
    ): Long {
        require(!timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        return repository.insert(
            DiaperChange(
                timestamp = timestamp,
                type = type,
                notes = notes?.takeIf { it.isNotBlank() },
                createdAt = now(),
            ),
        )
    }
}
```

- [ ] **Step 2: Test**

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class LogDiaperChangeUseCaseTest {
    private lateinit var repository: DiaperRepository
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: LogDiaperChangeUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = LogDiaperChangeUseCase(repository) { fixedNow }
    }

    @Test
    fun `inserts with stamped createdAt and trimmed notes`() = runTest {
        val captured = slot<DiaperChange>()
        coEvery { repository.insert(capture(captured)) } returns 1
        useCase(DiaperType.BOTH, Instant.ofEpochMilli(9_000), "   ")
        assertEquals(fixedNow, captured.captured.createdAt)
        assertEquals(null, captured.captured.notes)
        assertEquals(DiaperType.BOTH, captured.captured.type)
    }

    @Test
    fun `rejects future timestamps`() = runTest {
        val error = runCatching { useCase(DiaperType.WET, Instant.ofEpochMilli(20_000)) }
            .exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add LogDiaperChangeUseCase`

---

### Task 2: `EditDiaperChangeUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/diaper/EditDiaperChangeUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/diaper/EditDiaperChangeUseCaseTest.kt`

- [ ] **Step 1: Implement** (validates the edited timestamp, normalizes notes, delegates to `update`)

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import java.time.Instant
import javax.inject.Inject

class EditDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(change: DiaperChange) {
        require(!change.timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        repository.update(change.copy(notes = change.notes?.takeIf { it.isNotBlank() }))
    }
}
```

- [ ] **Step 2: Test**

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class EditDiaperChangeUseCaseTest {
    private lateinit var repository: DiaperRepository
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: EditDiaperChangeUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = EditDiaperChangeUseCase(repository) { fixedNow }
    }

    @Test
    fun `updates with blank notes normalized to null`() = runTest {
        val captured = slot<DiaperChange>()
        coVerify(exactly = 0) { repository.update(any()) }
        useCase(
            DiaperChange(
                id = 4,
                timestamp = Instant.ofEpochMilli(9_000),
                type = DiaperType.WET,
                notes = "  ",
                createdAt = Instant.ofEpochMilli(8_000),
            ),
        )
        coVerify { repository.update(capture(captured)) }
        assertEquals(null, captured.captured.notes)
    }

    @Test
    fun `rejects future timestamps`() = runTest {
        val error = runCatching {
            useCase(
                DiaperChange(
                    id = 4,
                    timestamp = Instant.ofEpochMilli(20_000),
                    type = DiaperType.WET,
                    createdAt = Instant.ofEpochMilli(8_000),
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add EditDiaperChangeUseCase`

---

### Task 3: `DeleteDiaperChangeUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/diaper/DeleteDiaperChangeUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/diaper/DeleteDiaperChangeUseCaseTest.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import javax.inject.Inject

class DeleteDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteById(id)
}
```

- [ ] **Step 2: Test**

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteDiaperChangeUseCaseTest {
    @Test
    fun `delegates to repository`() = runTest {
        val repository = mockk<DiaperRepository>(relaxed = true)
        DeleteDiaperChangeUseCase(repository)(5)
        coVerify { repository.deleteById(5) }
    }
}
```

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): add DeleteDiaperChangeUseCase`

---

### Task 4: `ObserveDiaperChangesUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/diaper/ObserveDiaperChangesUseCase.kt`

- [ ] **Step 1: Implement** (mirrors `ObserveBottleFeedsUseCase`)

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDiaperChangesUseCase @Inject constructor(
    private val repository: DiaperRepository,
) {
    operator fun invoke(): Flow<List<DiaperChange>> = repository.observeAll()
}
```

- [ ] **Step 2: Commit** `feat(diaper): add ObserveDiaperChangesUseCase`

---

### Task 5: `TodayDiaperSummary` + `ObserveTodayDiaperSummaryUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/TodayDiaperSummary.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/diaper/ObserveTodayDiaperSummaryUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/diaper/ObserveTodayDiaperSummaryUseCaseTest.kt`

- [ ] **Step 1: Summary model**

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class TodayDiaperSummary(
    val count: Int = 0,
    val lastChangeAt: Instant? = null,
) {
    val hasAny: Boolean get() = count > 0
}
```

- [ ] **Step 2: Use case** (mirrors `ObserveTodayFeedingSummaryUseCase`; `observeAll()` is timestamp-DESC, so the first element is the most recent change overall — `lastChangeAt` is independent of "today")

```kotlin
package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.TodayDiaperSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayDiaperSummaryUseCase @Inject constructor(
    private val observeDiaperChanges: ObserveDiaperChangesUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<TodayDiaperSummary> =
        observeDiaperChanges().map { changes ->
            val today = now().atZone(zone).toLocalDate()
            TodayDiaperSummary(
                count = changes.count { it.timestamp.atZone(zone).toLocalDate() == today },
                lastChangeAt = changes.maxByOrNull { it.timestamp }?.timestamp,
            )
        }
}
```

- [ ] **Step 3: Test** (Turbine; verify today-count excludes yesterday and `lastChangeAt` is the global max)

```kotlin
package com.babytracker.domain.usecase.diaper

import app.cash.turbine.test
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ObserveTodayDiaperSummaryUseCaseTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 6, 16, 12, 0, 0, 0, zone).toInstant()

    @Test
    fun `counts only today and reports global last change`() = runTest {
        val observe = mockk<ObserveDiaperChangesUseCase>()
        val todayMorning = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val todayLater = ZonedDateTime.of(2026, 6, 16, 11, 0, 0, 0, zone).toInstant()
        val yesterday = ZonedDateTime.of(2026, 6, 15, 23, 0, 0, 0, zone).toInstant()
        every { observe() } returns flowOf(
            // observeAll is DESC; provide in that order
            listOf(
                DiaperChange(id = 3, timestamp = todayLater, type = DiaperType.WET, createdAt = todayLater),
                DiaperChange(id = 2, timestamp = todayMorning, type = DiaperType.DIRTY, createdAt = todayMorning),
                DiaperChange(id = 1, timestamp = yesterday, type = DiaperType.BOTH, createdAt = yesterday),
            ),
        )
        val useCase = ObserveTodayDiaperSummaryUseCase(observe, zone) { now }

        useCase().test {
            val summary = awaitItem()
            assertEquals(2, summary.count)
            assertEquals(todayLater, summary.lastChangeAt)
            awaitComplete()
        }
    }

    @Test
    fun `empty history yields zero count and null last change`() = runTest {
        val observe = mockk<ObserveDiaperChangesUseCase>()
        every { observe() } returns flowOf(emptyList<DiaperChange>())
        ObserveTodayDiaperSummaryUseCase(observe, zone) { now }().test {
            val summary = awaitItem()
            assertEquals(0, summary.count)
            assertEquals(null, summary.lastChangeAt)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.domain.usecase.diaper.ObserveTodayDiaperSummaryUseCaseTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(diaper): add today diaper summary use case`

---

## Acceptance Criteria

- `./gradlew test` passes, including all new diaper use-case tests.
- All five use cases inject only the `DiaperRepository` interface (plus `() -> Instant` / `ZoneId` where needed); no Android or Firebase imports.
- Future timestamps are rejected by Log and Edit.
- `ObserveTodayDiaperSummaryUseCase` counts only the current local day but reports the most recent change regardless of day.
- No Firestore sync is invoked here (added in plan 5).

## Self-Review Notes

- Spec coverage: Log / Edit / Delete / Observe / today-summary + `TodayDiaperSummary` — all present.
- Type consistency: `LogDiaperChangeUseCase.invoke(type, timestamp, notes)` is the signature the plan-3 `DiaperViewModel` calls; `EditDiaperChangeUseCase.invoke(change)` takes a full `DiaperChange`. Keep these stable — plans 3 and 4 depend on them.
- The `() -> Instant` and `ZoneId` providers already exist (used by `ObserveTodayFeedingSummaryUseCase`), so no new DI providers are required.
