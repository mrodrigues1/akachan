# Partner Settings — Design Spec

**Date:** 2026-05-01
**Branch:** `feat/partner-settings`
**Status:** Approved

---

## Overview

The partner dashboard currently has no settings access. This spec adds a bottom `NavigationBar` to `PartnerDashboardScreen` with a single Settings item, navigating to a filtered view of the existing `SettingsScreen` that shows only the options relevant to partner users: Theme, Auto-update, and Disconnect.

---

## Goals

- Partner can change the app theme (Light / Dark / System)
- Partner can toggle the auto-update check
- Partner can disconnect (clear partner mode, return to onboarding/home)
- Navigation pattern is consistent with `HomeScreen` (bottom `NavigationBar`)

## Non-Goals

- Baby Profile, Feeding Limits, and Notifications settings are not exposed to the partner
- No new route or new screen composable
- No real-time sync or Firebase changes

---

## Design Decisions

### Entry point: bottom NavigationBar (not a TopAppBar icon)

`HomeScreen` uses a `NavigationBar` with Feeding / Sleep / Settings items. `PartnerDashboardScreen` mirrors this pattern with a single Settings `NavigationBarItem`. The TopAppBar (title + Refresh button) stays unchanged.

### Reuse existing SettingsScreen with PARTNER-mode filtering

Rather than creating a new `PartnerSettingsScreen`, the existing `SettingsScreen` conditionally hides sections irrelevant to partners:

| Section | PARTNER mode |
|---|---|
| Baby Profile | Hidden |
| Feeding Limits | Hidden |
| App Settings (Theme, Auto-update) | Shown |
| Notifications | Hidden |
| Partner Access | Shows Disconnect row |
| Developer (debug only) | Shown (unchanged) |

This avoids a new file and new route while keeping a single source of truth for settings UI components.

### Disconnect action

Tapping Disconnect in the Partner Access section runs the suspend DataStore writes to completion before navigation, using the same `isDisconnected` UiState event pattern already used in `PartnerDashboardViewModel`:

1. `SettingsViewModel.disconnect()` launches a coroutine that calls `setAppMode(NONE)` then `clearShareCode()`, then sets `uiState.isDisconnected = true`
2. `SettingsScreen` observes `isDisconnected` via `LaunchedEffect` and calls `onDisconnect()` only after the flag is set — guaranteeing the DataStore writes complete before the back stack is popped and `SettingsViewModel` is cleared

`AppNavGraph` wires `onDisconnect` to navigate to `Routes.ONBOARDING` or `Routes.HOME` with `popUpTo(0) { inclusive = true }`.

### AppMode loading guard

`SettingsUiState.appMode` defaults to `AppMode.NONE` while the repository `combine` flow loads. A PARTNER user opening Settings would briefly see the Baby Profile, Feeding Limits, and Notifications sections before the real app mode arrives. To prevent this, `appMode` is modelled as `AppMode?` (nullable) with a `null` default. Restricted sections are only rendered when `appMode` is known and not `PARTNER`; the Disconnect row is only rendered when `appMode == AppMode.PARTNER`.

### Disconnect row button label

`SettingsRow` hardcodes a trailing `TextButton` labelled `"Edit"`. The Disconnect row must not show `"Edit"`. `SettingsRow` gains an `actionLabel: String = "Edit"` parameter so callers can override the button text. The Disconnect row passes `actionLabel = "Disconnect"` and colours it with `MaterialTheme.colorScheme.error` to signal the destructive nature of the action.

---

## Component Changes

### `PartnerDashboardScreen.kt`

- Add `onNavigateToSettings: () -> Unit` parameter
- Add `bottomBar` to `Scaffold`:
  ```kotlin
  NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
      NavigationBarItem(
          icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
          label = { Text("Settings") },
          selected = false,
          onClick = onNavigateToSettings,
      )
  }
  ```

### `AppNavGraph.kt`

- `PARTNER_DASHBOARD` composable gets `onNavigateToSettings` wired to `navController.navigate(Routes.SETTINGS)`
- `SETTINGS` composable gets `onDisconnect` wired to navigate to `Routes.ONBOARDING` or `Routes.HOME` with `popUpTo(0) { inclusive = true }`

### `SettingsScreen.kt`

- Add `onDisconnect: () -> Unit = {}` parameter
- Add `LaunchedEffect(uiState.isDisconnected)` that calls `onDisconnect()` when `isDisconnected == true`
- Wrap Baby Profile, Feeding Limits, and Notifications sections with `if (uiState.appMode != null && uiState.appMode != AppMode.PARTNER)` — renders nothing while loading, hides for PARTNER
- In the `AppMode.PARTNER` branch of the Partner Access section (currently `Unit`), add:
  ```kotlin
  if (uiState.appMode == AppMode.PARTNER) {
      SettingsRow(
          label = "Disconnect",
          value = "Stop viewing as partner",
          actionLabel = "Disconnect",
          actionColor = MaterialTheme.colorScheme.error,
          onClick = viewModel::disconnect,
      )
  }
  ```

### `SettingsRow` (private composable in `SettingsScreen.kt`)

- Add `actionLabel: String = "Edit"` and `actionColor: Color = MaterialTheme.colorScheme.primary` parameters
- Replace hardcoded `Text("Edit")` with `Text(actionLabel, color = actionColor)`

### `SettingsViewModel.kt`

- Change `appMode: AppMode = AppMode.NONE` → `appMode: AppMode? = null` in `SettingsUiState`
- Add `isDisconnected: Boolean = false` to `SettingsUiState`
- Add `disconnect()` function:
  ```kotlin
  fun disconnect() {
      viewModelScope.launch {
          settingsRepository.setAppMode(AppMode.NONE)
          settingsRepository.clearShareCode()
          _uiState.update { it.copy(isDisconnected = true) }
      }
  }
  ```

---

## Data Flow

```
Partner taps Settings
        ↓
NavigationBarItem.onClick → onNavigateToSettings()
        ↓
AppNavGraph navigates to Routes.SETTINGS
        ↓
SettingsScreen renders with appMode == PARTNER
  → hides Baby Profile, Feeding Limits, Notifications
  → shows Theme, Auto-update, Disconnect
        ↓
Partner taps Disconnect
        ↓
SettingsViewModel.disconnect() [coroutine]
  → setAppMode(NONE)
  → clearShareCode()
  → uiState.isDisconnected = true
        ↓
SettingsScreen LaunchedEffect(isDisconnected) fires
        ↓
onDisconnect() callback
        ↓
AppNavGraph navigates to HOME or ONBOARDING (full stack pop)
```

---

## Testing

- **Unit**: `SettingsViewModelTest` — add test for `disconnect()` verifying `setAppMode(NONE)` and `clearShareCode()` are both called in order, and that `isDisconnected` becomes `true` only after both writes complete
- **Unit**: `SettingsViewModelTest` — add test verifying `appMode` is `null` initially and emits the real value once the combine flow fires
- **UI**: `PartnerDashboardScreen` — verify Settings `NavigationBarItem` is displayed; verify tapping it triggers `onNavigateToSettings`
- **UI**: `SettingsScreen` (PARTNER mode) — verify Baby Profile, Feeding Limits, Notifications sections are absent; verify Disconnect row is present with "Disconnect" button label (not "Edit")
