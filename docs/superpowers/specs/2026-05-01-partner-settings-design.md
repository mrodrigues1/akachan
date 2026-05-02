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

Tapping Disconnect in the Partner Access section:
1. Calls `settingsRepository.setAppMode(AppMode.NONE)`
2. Calls `settingsRepository.clearShareCode()`
3. Navigates to onboarding if onboarding is not complete, otherwise to home — same logic as the existing `isDisconnected` flow in `PartnerDashboardScreen`

The disconnect callback flows: `SettingsViewModel.disconnect()` → `onDisconnect` lambda → `AppNavGraph` navigates to `Routes.ONBOARDING` or `Routes.HOME` with full back-stack pop.

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
- Wrap Baby Profile, Feeding Limits, and Notifications sections with `if (uiState.appMode != AppMode.PARTNER)`
- In the `AppMode.PARTNER` branch of the Partner Access section (currently `Unit`), add:
  ```kotlin
  SettingsRow(
      label = "Disconnect",
      value = "Stop viewing as partner",
      onClick = {
          viewModel.disconnect()
          onDisconnect()
      },
  )
  ```

### `SettingsViewModel.kt`

- Add `disconnect()` function:
  ```kotlin
  fun disconnect() {
      viewModelScope.launch {
          settingsRepository.setAppMode(AppMode.NONE)
          settingsRepository.clearShareCode()
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
SettingsViewModel.disconnect()
  → setAppMode(NONE) + clearShareCode()
        ↓
onDisconnect() callback
        ↓
AppNavGraph navigates to HOME or ONBOARDING (full stack pop)
```

---

## Testing

- **Unit**: `SettingsViewModelTest` — add test for `disconnect()` verifying `setAppMode(NONE)` and `clearShareCode()` are both called
- **UI**: `PartnerDashboardScreen` — verify Settings `NavigationBarItem` is displayed; verify tapping it triggers `onNavigateToSettings`
- **UI**: `SettingsScreen` (PARTNER mode) — verify Baby Profile, Feeding Limits, Notifications sections are absent; verify Disconnect row is present
