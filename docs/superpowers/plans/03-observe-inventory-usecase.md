# ObserveInventoryWithExpirationUseCase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-76

**Goal:** Add a use case that combines the active-bags Flow with the expiration settings and an externally-driven date Flow, emitting `List<MilkBagWithExpiration>` with each bag's computed `ExpirationStatus`.

**Architecture:** Pure-domain use case with `operator fun invoke(dateFlow: Flow<LocalDate>)`. Date is injected as a Flow (not `LocalDate.now()` inline) so the ViewModel can push a fresh date on resume — a static `combine` transform only re-runs when an upstream Flow emits, so calling `now()` inline would let statuses go stale if the screen stays open past midnight. When the feature is disabled, every bag maps to `NONE` and no date math runs.

**Tech Stack:** Kotlin, Coroutines Flow `combine`, `java.time` (`LocalDate`, `ZoneId`).

**Dependencies:** AKA-74 (`ExpirationStatus`, `MilkBagWithExpiration`), AKA-75 (`InventorySettingsRepository`). Reuses existing `GetInventoryUseCase` (returns `Flow<List<MilkBag>>` of active bags).

**Suggested implementation branch:** `feat/aka-76-observe-usecase`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/ObserveInventoryWithExpirationUseCase.kt`

Lives beside the other inventory use cases (`GetInventoryUseCase.kt`, `AddMilkBagUseCase.kt`). Follows the `@Inject constructor` + `operator fun invoke` convention. Unit tests for this class are written in AKA-82 (this plan only verifies compilation; full Turbine test coverage is in the tests plan).

---

## Boundary semantics (locked, referenced by AKA-82 tests)

Given `expiryDate = bag.collectionDate (system zone) .toLocalDate() + days`:

| Relationship to `today` | Status |
|-------------------------|--------|
| `expiryDate` is after `today + 1` (≥ 2 days away) | `NONE` |
| `expiryDate == today + 1` (exactly tomorrow) | `EXPIRING_SOON` |
| `expiryDate == today` or any date before today | `EXPIRING_OR_EXPIRED` |

The `when` ordering matters: test `isAfter(today.plusDays(1))` first, then `isAfter(today)`, else fall through to expired.

---

## Task 1: Create the use case

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/ObserveInventoryWithExpirationUseCase.kt`

- [ ] **Step 1: Write the use case**

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.MilkBagWithExpiration
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Combines active milk bags + expiration settings + the current date into a stream of
 * bags tagged with their [ExpirationStatus].
 *
 * [dateFlow] is supplied by the caller (the ViewModel) rather than read via LocalDate.now()
 * inline: a combine transform only re-evaluates when an upstream flow emits, so an inline
 * now() would leave statuses stale if the screen is left open past midnight. The ViewModel
 * pushes a fresh LocalDate on resume.
 */
class ObserveInventoryWithExpirationUseCase @Inject constructor(
    private val getInventory: GetInventoryUseCase,
    private val settings: InventorySettingsRepository,
) {
    operator fun invoke(dateFlow: Flow<LocalDate>): Flow<List<MilkBagWithExpiration>> =
        combine(
            getInventory(),
            settings.getExpirationEnabled(),
            settings.getExpirationDays(),
            dateFlow,
        ) { bags, enabled, days, today ->
            if (!enabled) {
                bags.map { MilkBagWithExpiration(it, ExpirationStatus.NONE) }
            } else {
                bags.map { bag ->
                    val expiryDate = bag.collectionDate
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .plusDays(days.toLong())
                    val status = when {
                        expiryDate.isAfter(today.plusDays(1)) -> ExpirationStatus.NONE
                        expiryDate.isAfter(today) -> ExpirationStatus.EXPIRING_SOON
                        else -> ExpirationStatus.EXPIRING_OR_EXPIRED
                    }
                    MilkBagWithExpiration(bag, status)
                }
            }
        }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 2: Quality gates and commit

- [ ] **Step 1: ktlint + detekt**

Run: `./gradlew ktlintFormat detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run architecture tests (domain purity)**

The use case lives in the domain layer; confirm no framework imports slipped in.

Run (WSL): `./gradlew :app:testDebugUnitTest --tests "com.babytracker.architecture.*"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/inventory/ObserveInventoryWithExpirationUseCase.kt
git commit -m "feat(inventory): add ObserveInventoryWithExpirationUseCase [AKA-76]"
```

---

## Acceptance Criteria

- `invoke(dateFlow)` emits a fresh list whenever **any** of the four upstreams emit (bags, enabled, days, date).
- Feature disabled → all bags `NONE`, and no `expiryDate` computation runs (verified by the `if (!enabled)` early branch).
- `EXPIRING_SOON` ⇔ expires exactly tomorrow; `EXPIRING_OR_EXPIRED` ⇔ expires today or earlier; `NONE` ⇔ ≥ 2 days out.
- Empty bag list → empty list emitted.
- No `LocalDate.now()` is called inside the `combine` transform.
- Full behavioural coverage is added in AKA-82.
