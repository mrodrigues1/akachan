# Navigation Wiring (INVENTORY_SETTINGS route) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-81

**Goal:** Register the `inventory/settings` route, add the `InventorySettingsScreen` destination to the nav graph, and pass `onNavigateToSettings` into `InventoryScreen`.

**Architecture:** Pure navigation glue. Adds one route constant and one `composable` destination, and updates the existing `Routes.INVENTORY` destination to supply the settings-nav lambda. Follows the established `AppNavGraph` pattern (each screen gets `onNavigateBack = { navController.popBackStack() }`).

**Tech Stack:** Compose Navigation 2.9.7.

**Dependencies:** AKA-77 (`InventorySettingsScreen` composable exists), AKA-78 (`InventoryScreen` accepts `onNavigateToSettings`). This is the integration plan that ties them together — it should land **after** both.

**Suggested implementation branch:** `feat/aka-81-nav`

---

## File Structure

- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

---

## Task 1: Add the route constant

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`

- [ ] **Step 1: Add the constant** (after `INVENTORY`)

```kotlin
    const val INVENTORY_SETTINGS = "inventory/settings"
```

- [ ] **Step 2: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 2: Wire the destinations

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1: Pass `onNavigateToSettings` to the existing INVENTORY destination**

Update the current `composable(Routes.INVENTORY)` block:

```kotlin
composable(Routes.INVENTORY) {
    InventoryScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToSettings = { navController.navigate(Routes.INVENTORY_SETTINGS) },
    )
}
```

- [ ] **Step 2: Add the INVENTORY_SETTINGS destination** (immediately after the INVENTORY block)

```kotlin
composable(Routes.INVENTORY_SETTINGS) {
    InventorySettingsScreen(onNavigateBack = { navController.popBackStack() })
}
```

Add the import: `com.babytracker.ui.inventory.InventorySettingsScreen`.

- [ ] **Step 3: Verify it compiles** — `./gradlew :app:compileDebugKotlin`

---

## Task 3: Confirm the notification deep-link still resolves

The stash notification (AKA-79) taps to `Routes.INVENTORY` via `EXTRA_NAV_ROUTE`. That route already existed before this plan, so no new deep-link handling is required here.

- [ ] **Step 1: Verify the existing `EXTRA_NAV_ROUTE` handler covers `Routes.INVENTORY`**

Inspect how `EXTRA_NAV_ROUTE` is consumed (the same mechanism the nap reminder uses for `Routes.SLEEP_TRACKING`). Confirm it navigates to the extra's route generically (so `Routes.INVENTORY` resolves) rather than switching on a hardcoded allow-list. If it is a hardcoded `when`, add an `INVENTORY` branch.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 4: Quality gates and commit

- [ ] **Step 1: ktlint + detekt** — `./gradlew ktlintFormat detekt` → BUILD SUCCESSFUL
- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/navigation/Routes.kt \
        app/src/main/java/com/babytracker/navigation/AppNavGraph.kt
git commit -m "feat(navigation): wire inventory settings route [AKA-81]"
```

---

## Acceptance Criteria

- `Routes.INVENTORY_SETTINGS == "inventory/settings"`.
- `InventoryScreen`'s gear icon navigates to the settings screen; back returns to the inventory list.
- `InventorySettingsScreen` is registered as a destination with a working back action.
- Notification tap to `Routes.INVENTORY` still resolves (verified, not assumed).
- App builds.
