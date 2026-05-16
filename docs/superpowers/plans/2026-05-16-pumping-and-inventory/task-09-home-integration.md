# Task 9 — Home screen 2×2 grid integration

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Replace the single-row summary block in `HomeScreen` with a 2×2 grid: Feeding/Sleep on row 1 (existing cards), Pumping/Inventory on row 2 (new cards). Bottom action bar stays Feeding/Sleep — no third button.

**Depends on:** Task 6 (`Routes.PUMPING` registered), Task 8 (`Routes.INVENTORY` registered).

## Files

- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`
- Test: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt` *(extend or create)*
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/home/HomeScreenTest.kt` *(extend or create)*

## Implementation

### Step 1: `HomeViewModel`

Add two pieces of state to `HomeUiState`:

- `pumpingActive: PumpingSession?` (nullable) — collected from `PumpingRepository.getActiveSession()`.
- `inventorySummary: InventorySummary` — collected from `InventoryRepository.getSummary()` (defaults to `InventorySummary.Empty`).

Inject `PumpingRepository` and `InventoryRepository`. Combine them with the existing flows in the `init` block. Match the existing `combine { ... }` shape used for `BreastfeedingRepository.getActiveSession()`. Read the current file for the exact `combine` arity and add the two new sources to that emission.

Result: `HomeUiState` gains two read-only fields the home composable can render.

### Step 2: `HomeScreen`

Restructure the summary area. The existing `Row` containing the breastfeeding + sleep cards becomes the first row of a `Column` with `spacedBy(12.dp)`. Add a second `Row` containing the new cards.

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    // existing Breastfeeding card  — Modifier.weight(1f)
    // existing Sleep card           — Modifier.weight(1f)
}
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    PumpingHomeCard(
        active = uiState.pumpingActive,
        onClick = onNavigateToPumping,
        modifier = Modifier.weight(1f),
    )
    InventoryHomeCard(
        summary = uiState.inventorySummary,
        onClick = onNavigateToInventory,
        modifier = Modifier.weight(1f),
    )
}
```

Add the two new private composables in the same file (mirrors how the existing cards are inlined). Card colors per the spec:

- **Pumping card** — `MaterialTheme.colorScheme.tertiaryContainer`, badge `Icons.Outlined.WaterDrop` or `"🥛"`. Body: `"Live ${elapsed}"` when `active != null`, else `"Last X ago"` (use the new `pumpingActive` + history-derived "last X" — for v1 just show "Tap to log" when no active session to keep scope tight). Match the existing breastfeeding-card pattern for animations.
- **Inventory card** — `MaterialTheme.colorScheme.surfaceVariant`, badge `"🧊"`. Body: `"${summary.totalMl} mL · ${summary.bagCount} bags"` when `summary.bagCount > 0`, else `"No bags stored"`.

Add two new params to `HomeScreen`:

```kotlin
onNavigateToPumping: () -> Unit = {},
onNavigateToInventory: () -> Unit = {},
```

Update `contentDescription` semantics on each card mirroring the existing pattern ("Pumping, session active. Open pumping screen." vs "Pumping. Open pumping screen.").

### Step 3: AppNavGraph

Wire the new callbacks:

```kotlin
HomeScreen(
    onNavigateToBreastfeeding = { navController.navigate(Routes.BREASTFEEDING) },
    onNavigateToSleep = { navController.navigate(Routes.SLEEP_TRACKING) },
    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
    onNavigateToConnectPartner = { navController.navigate(Routes.CONNECT_PARTNER) },
    onNavigateToPumping = { navController.navigate(Routes.PUMPING) },
    onNavigateToInventory = { navController.navigate(Routes.INVENTORY) },
)
```

Bottom action bar stays untouched.

## Tests

### `HomeViewModelTest`

- Emits `pumpingActive = session` when `PumpingRepository.getActiveSession()` emits a session.
- `inventorySummary` flows through unchanged.
- When both flows are empty, defaults to `null` / `InventorySummary.Empty`.

### `HomeScreenTest`

- Renders four summary cards in a 2×2 grid.
- Pumping card with no active session shows "Tap to log".
- Pumping card with an active session shows "Live" badge.
- Inventory card with `totalMl = 0, bagCount = 0` shows "No bags stored".
- Inventory card with `totalMl = 240, bagCount = 3` shows "240 mL · 3 bags".
- Tapping each card invokes the matching `onNavigateTo*` callback.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTest"
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
com.babytracker.ui.home.HomeScreenTest
```

Expected: all green. Manually: launch app, confirm both new cards appear and route correctly.

## Commit

```
feat(home): add Pumping and Inventory cards to summary grid

Reshapes the home summary into a 2x2 grid. New cards link to PumpingScreen
and InventoryScreen. HomeViewModel collects PumpingRepository.getActive
and InventoryRepository.getSummary to keep the cards live.
```
