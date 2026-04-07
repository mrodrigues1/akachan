# Specification: Theme Selection Feature (Light, Dark, System)

## 1. Goal
Add a theme selection feature that allows the user to choose between Light mode, Dark mode, or follow the System theme.

## 2. Technical Details

### 2.1 Theme Configuration Enum
A new enum `ThemeConfig` will be created to represent the possible theme options:
- `SYSTEM`: Follow the system's dark/light theme (default).
- `LIGHT`: Always use the light theme.
- `DARK`: Always use the dark theme.

### 2.2 Data Persistence
The selected theme preference will be stored in `DataStore` via `SettingsRepository`.
- Key: `theme_config` (String)
- Default: `SYSTEM`

### 2.3 Data Flow
1. `SettingsRepository` will provide a `Flow<ThemeConfig>` to observe the current theme setting.
2. `MainActivity` will observe this Flow.
3. The observed `ThemeConfig` will be passed to `BabyTrackerTheme`.
4. `BabyTrackerTheme` will determine the actual `darkTheme: Boolean` based on the `ThemeConfig` and `isSystemInDarkTheme()`.

### 2.4 UI Updates
1. `SettingsScreen` will include a new row for "Theme".
2. Clicking the "Theme" row will open a bottom sheet or a dialog with three options: Light, Dark, System.
3. `SettingsViewModel` will handle the logic to update the theme setting in `SettingsRepository`.

## 3. Implementation Plan

1. Create `com.babytracker.domain.model.ThemeConfig` enum.
2. Update `SettingsRepository` and `SettingsRepositoryImpl` with `getThemeConfig()` and `setThemeConfig()`.
3. Update `SettingsUiState` and `SettingsViewModel` to include `themeConfig`.
4. Update `BabyTrackerTheme` in `Theme.kt` to accept `ThemeConfig`.
5. Update `MainActivity` to observe `themeConfig` and pass it to `BabyTrackerTheme`.
6. Update `SettingsScreen` to add the theme selection UI.
