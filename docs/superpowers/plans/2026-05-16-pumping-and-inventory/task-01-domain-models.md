# Task 1 — Domain models

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Add the four pure-Kotlin domain types the rest of the feature depends on.

**Why first:** Repositories, use cases, ViewModels, and snapshot helpers all consume these. Nothing else can compile without them.

## Files

- Create: `app/src/main/java/com/babytracker/domain/model/PumpingBreast.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/PumpingSession.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/MilkBag.kt`
- Create: `app/src/main/java/com/babytracker/domain/model/InventorySummary.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/PumpingSessionTest.kt`
- Test: `app/src/test/java/com/babytracker/domain/model/MilkBagTest.kt`

## Implementation

### Step 1: `PumpingBreast.kt`

```kotlin
package com.babytracker.domain.model

enum class PumpingBreast { LEFT, RIGHT, BOTH }

fun PumpingBreast.displayName(): String = when (this) {
    PumpingBreast.LEFT -> "Left"
    PumpingBreast.RIGHT -> "Right"
    PumpingBreast.BOTH -> "Both"
}
```

### Step 2: `PumpingSession.kt`

```kotlin
package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant

data class PumpingSession(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val breast: PumpingBreast,
    val volumeMl: Int? = null,
    val notes: String? = null,
    val pausedAt: Instant? = null,
    val pausedDurationMs: Long = 0,
) {
    val isInProgress: Boolean get() = endTime == null
    val isPaused: Boolean get() = pausedAt != null
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it).minusMillis(pausedDurationMs).coerceAtLeast(Duration.ZERO) }

    fun activeDurationUntil(until: Instant): Duration =
        Duration.between(startTime, endTime ?: until)
            .minusMillis(pausedDurationMs)
            .coerceAtLeast(Duration.ZERO)
}
```

### Step 3: `MilkBag.kt`

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class MilkBag(
    val id: Long = 0,
    val collectionDate: Instant,
    val volumeMl: Int,
    val sourceSessionId: Long? = null,
    val usedAt: Instant? = null,
    val notes: String? = null,
    val createdAt: Instant,
) {
    val isActive: Boolean get() = usedAt == null
}
```

### Step 4: `InventorySummary.kt`

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class InventorySummary(
    val totalMl: Int,
    val bagCount: Int,
    val oldestBagDate: Instant?,
) {
    companion object {
        val Empty = InventorySummary(totalMl = 0, bagCount = 0, oldestBagDate = null)
    }
}
```

## Tests

After the four files compile, add unit tests.

### `PumpingSessionTest.kt`

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PumpingSessionTest {
    private val start = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `isInProgress true when endTime null`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        assertTrue(session.isInProgress)
    }

    @Test
    fun `duration subtracts pausedDurationMs and floors at zero`() {
        val end = start.plus(Duration.ofMinutes(20))
        val session = PumpingSession(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.BOTH,
            pausedDurationMs = Duration.ofMinutes(5).toMillis(),
        )
        assertEquals(Duration.ofMinutes(15), session.duration)
    }

    @Test
    fun `duration null when in progress`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        assertEquals(null, session.duration)
    }

    @Test
    fun `activeDurationUntil uses provided clock when in progress`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        val now = start.plus(Duration.ofMinutes(8))
        assertEquals(Duration.ofMinutes(8), session.activeDurationUntil(now))
    }

    @Test
    fun `isPaused reflects pausedAt`() {
        val session = PumpingSession(
            startTime = start,
            breast = PumpingBreast.RIGHT,
            pausedAt = start.plus(Duration.ofMinutes(2)),
        )
        assertTrue(session.isPaused)
        assertFalse(session.copy(pausedAt = null).isPaused)
    }
}
```

### `MilkBagTest.kt`

```kotlin
package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MilkBagTest {
    private val now = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `isActive true when usedAt null`() {
        val bag = MilkBag(collectionDate = now, volumeMl = 120, createdAt = now)
        assertTrue(bag.isActive)
    }

    @Test
    fun `isActive false when usedAt set`() {
        val bag = MilkBag(collectionDate = now, volumeMl = 120, createdAt = now, usedAt = now)
        assertFalse(bag.isActive)
    }
}
```

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.domain.model.*"
```

Expected: all green.

## Commit

```
feat(pumping): add domain models for pumping sessions and milk bags

Introduce PumpingSession, PumpingBreast, MilkBag, and InventorySummary
pure-Kotlin data classes. Foundation for the pumping + inventory feature.
```
