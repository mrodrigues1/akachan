# Inventory Expiration Colors + Settings Nav Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Invoke the `mobile-android-design` skill when editing the composable.

**LINEAR_ISSUE:** AKA-78

**Goal:** Wire expiration into the existing inventory list — swap the ViewModel to the expiration-aware use case, add a recompute-on-resume date Flow, tint `MilkBagRow` by `ExpirationStatus`, and add a gear icon that navigates to the settings screen.

**Architecture:** `InventoryViewModel` swaps `GetInventoryUseCase` → `ObserveInventoryWithExpirationUseCase`, owns a `MutableStateFlow<LocalDate>` pushed fresh by `onResume()`, and exposes `bags: List<MilkBagWithExpiration>`. The screen drives `onResume()` via `repeatOnLifecycle(RESUMED)` so statuses recompute every time the user returns (covers the past-midnight stale-status case from AKA-76). `MilkBagRow` colors come from the non-M3 warning tokens imported directly from `ui.theme.Color`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Lifecycle `repeatOnLifecycle`, Coroutines/Flow, `java.time.LocalDate`.

**Dependencies:** AKA-76 (`ObserveInventoryWithExpirationUseCase`), AKA-74 (`ExpirationStatus`, `MilkBagWithExpiration`). Navigation param (`onNavigateToSettings`) is consumed by AKA-81.

**Suggested implementation branch:** `feat/aka-78-inventory-colors`

---

## File Structure

- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt`
- Test: `app/src/test/java/com/babytracker/ui/inventory/InventoryViewModelTest.kt` (create or extend)

---

## Color mapping (from issue) + precedence decision

| Status | `containerColor` | `contentColor` |
|--------|------------------|----------------|
| `NONE` | existing behaviour (oldest → `tertiaryContainer`, else `surface`) | matching existing |
| `EXPIRING_SOON` | `WarningContainerAmber.copy(alpha = 0.45f)` | `OnWarningContainerAmber` |
| `EXPIRING_OR_EXPIRED` | `WarningContainerAmber` | `OnWarningContainerAmber` |

**Precedence:** when `status != NONE`, the amber expiration tint **overrides** the existing "oldest bag" `tertiaryContainer` highlight — expiration is the more urgent signal. For `NONE`, the current oldest/surface logic is preserved unchanged (feature-off path also yields all-`NONE`, so disabled state looks exactly as today).

> Import `WarningContainerAmber` and `OnWarningContainerAmber` directly from `com.babytracker.ui.theme` — **not** via `MaterialTheme.colorScheme` (they are extended, non-M3 tokens per CLAUDE.md).

---

## Task 1: ViewModel tests (TDD)

**Files:**
- Test: `app/src/test/java/com/babytracker/ui/inventory/InventoryViewModelTest.kt`

Mock `ObserveInventoryWithExpirationUseCase` (concrete class — MockK can mock it), `GetInventorySummaryUseCase`, and the action use cases. The use case is invoked with a `Flow<LocalDate>`; stub `every { observeInventory(any()) } returns flowOf(listOf(...))`.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.babytracker.ui.inventory

import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.MilkBagWithExpiration
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.inventory.DeleteMilkBagUseCase
import com.babytracker.domain.usecase.inventory.GetInventorySummaryUseCase
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import com.babytracker.domain.usecase.inventory.ObserveInventoryWithExpirationUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class InventoryViewModelTest {

    private lateinit var observeInventory: ObserveInventoryWithExpirationUseCase
    private lateinit var getSummary: GetInventorySummaryUseCase
    private lateinit var addBag: AddMilkBagUseCase
    private lateinit var markUsed: MarkBagUsedUseCase
    private lateinit var deleteBag: DeleteMilkBagUseCase
    private val now: () -> Instant = { Instant.parse("2026-06-02T10:00:00Z") }

    private val sampleBag = MilkBag(
        id = 1,
        collectionDate = Instant.parse("2026-06-01T10:00:00Z"),
        volumeMl = 100,
        createdAt = Instant.parse("2026-06-01T10:00:00Z"),
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        observeInventory = mockk()
        getSummary = mockk()
        addBag = mockk(relaxed = true)
        markUsed = mockk(relaxed = true)
        deleteBag = mockk(relaxed = true)
        every { observeInventory(any<Flow<LocalDate>>()) } returns
            flowOf(listOf(MilkBagWithExpiration(sampleBag, ExpirationStatus.EXPIRING_OR_EXPIRED)))
        every { getSummary() } returns flowOf(InventorySummary.Empty)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = InventoryViewModel(observeInventory, getSummary, addBag, markUsed, deleteBag, now)

    @Test
    fun `exposes bags with expiration status`() = runTest {
        val viewModel = vm()
        runCurrent()
        val bags = viewModel.uiState.value.bags
        assertEquals(1, bags.size)
        assertEquals(ExpirationStatus.EXPIRING_OR_EXPIRED, bags.first().status)
        assertEquals(1L, bags.first().bag.id)
    }

    @Test
    fun `onMarkUsed delegates the underlying MilkBag`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onMarkUsed(sampleBag)
        runCurrent()
        coVerify { markUsed(sampleBag) }
    }

    @Test
    fun `onResume does not crash and keeps bags`() = runTest {
        val viewModel = vm()
        runCurrent()
        viewModel.onResume()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.bags.size)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.inventory.InventoryViewModelTest" -PfastTests`

---

## Task 2: Update InventoryViewModel

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt`

- [ ] **Step 1: Change `InventoryUiState.bags` type**

```kotlin
data class InventoryUiState(
    val summary: InventorySummary = InventorySummary.Empty,
    val bags: List<MilkBagWithExpiration> = emptyList(),
    val addSheet: AddBagSheetState? = null,
    val error: String? = null,
)
```

- [ ] **Step 2: Swap the use case, add the date flow + onResume**

Replace the `getInventory: GetInventoryUseCase` constructor param and the `init` combine. New constructor + init:

```kotlin
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val observeInventory: ObserveInventoryWithExpirationUseCase,
    getSummary: GetInventorySummaryUseCase,
    private val addBag: AddMilkBagUseCase,
    private val markUsed: MarkBagUsedUseCase,
    private val deleteBag: DeleteMilkBagUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val currentDate = MutableStateFlow(LocalDate.now())

    init {
        viewModelScope.launch {
            combine(observeInventory(currentDate), getSummary()) { bags, summary -> bags to summary }
                .collect { (bags, summary) ->
                    _uiState.value = _uiState.value.copy(bags = bags, summary = summary)
                }
        }
    }

    fun onResume() {
        currentDate.value = LocalDate.now()
    }
    // ... onAddBagClicked / onAddBagConfirm / onMarkUsed / onDelete / onError unchanged ...
}
```

Add imports: `com.babytracker.domain.model.MilkBagWithExpiration`, `com.babytracker.domain.usecase.inventory.ObserveInventoryWithExpirationUseCase`, `java.time.LocalDate`. Remove the now-unused `GetInventoryUseCase` import. `onMarkUsed(bag: MilkBag)` / `onDelete(bag: MilkBag)` keep their `MilkBag` signatures (the screen passes `item.bag`).

- [ ] **Step 3: Run ViewModel tests, expect PASS** (3 tests)

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.inventory.InventoryViewModelTest" -PfastTests`

---

## Task 3: Update InventoryScreen + MilkBagRow

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt`

- [ ] **Step 1: Add the settings nav param + gear icon + resume hook**

`InventoryScreen` signature gains `onNavigateToSettings: () -> Unit`. Add to `TopAppBar` `actions`:

```kotlin
actions = {
    IconButton(onClick = onNavigateToSettings) {
        Icon(Icons.Default.Settings, contentDescription = "Milk stash settings")
    }
},
```

Add the resume hook inside the composable body (drives `viewModel.onResume()` on every RESUMED):

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.onResume()
    }
}
```

New imports: `androidx.compose.material.icons.filled.Settings`, `androidx.lifecycle.compose.LocalLifecycleOwner`, `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.repeatOnLifecycle`.

- [ ] **Step 2: Thread `MilkBagWithExpiration` through `InventoryContent`**

`InventoryContent` already iterates `state.bags`. Update the item lambda to pass status and unwrap `.bag`:

```kotlin
items(state.bags, key = { it.bag.id }) { item ->
    val isOldest = item.bag.id == state.bags.first().bag.id
    MilkBagRow(
        bag = item.bag,
        expirationStatus = item.status,
        isOldest = isOldest,
        onMarkUsed = { onMarkUsed(item.bag) },
        onDelete = { onDelete(item.bag) },
    )
}
```

`onMarkUsed` / `onDelete` callback types in `InventoryContent` stay `(MilkBag) -> Unit`. The empty-state check `state.bags.isEmpty()` is unchanged.

- [ ] **Step 3: Add `expirationStatus` to `MilkBagRow` and apply colors**

```kotlin
@Composable
private fun MilkBagRow(
    bag: MilkBag,
    expirationStatus: ExpirationStatus,
    isOldest: Boolean,
    onMarkUsed: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val containerColor = when (expirationStatus) {
        ExpirationStatus.EXPIRING_OR_EXPIRED -> WarningContainerAmber
        ExpirationStatus.EXPIRING_SOON -> WarningContainerAmber.copy(alpha = 0.45f)
        ExpirationStatus.NONE -> if (isOldest) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    }
    val contentColor = when (expirationStatus) {
        ExpirationStatus.NONE -> if (isOldest) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        else -> OnWarningContainerAmber
    }
    // ... existing Card/Row body, using containerColor + contentColor as before ...
}
```

The inner tint logic that currently keys off `isOldest` (the "Pumped … ago" text color and icon tints) should fall back to `contentColor` when `expirationStatus != NONE` so it stays legible on amber. Simplest: for non-`NONE` statuses use `contentColor` for those secondary tints instead of `tertiary`/`primary`.

Add imports: `com.babytracker.domain.model.ExpirationStatus`, `com.babytracker.ui.theme.WarningContainerAmber`, `com.babytracker.ui.theme.OnWarningContainerAmber`.

- [ ] **Step 4: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 4: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Tests** — `./gradlew :app:testDebugUnitTest --tests "com.babytracker.ui.inventory.InventoryViewModelTest" -PfastTests` → PASS
- [ ] **Step 3: Build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/inventory/InventoryViewModel.kt \
        app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt \
        app/src/test/java/com/babytracker/ui/inventory/InventoryViewModelTest.kt
git commit -m "feat(inventory): tint bags by expiration status and add settings nav [AKA-78]"
```

---

## Acceptance Criteria

- `InventoryUiState.bags` is `List<MilkBagWithExpiration>`; VM uses `ObserveInventoryWithExpirationUseCase` driven by a `MutableStateFlow<LocalDate>`.
- `onResume()` refreshes the date; the screen calls it via `repeatOnLifecycle(RESUMED)`.
- `MilkBagRow` tints: `EXPIRING_OR_EXPIRED` → full `WarningContainerAmber`; `EXPIRING_SOON` → 45% alpha; `NONE` → existing oldest/surface behaviour. Amber overrides the oldest highlight.
- Warning tokens imported directly from `ui.theme`, not via `MaterialTheme.colorScheme`.
- Gear icon in the TopAppBar invokes `onNavigateToSettings`.
- With the feature disabled (all `NONE`), the list looks identical to today.
- ViewModel tests pass; app builds.
