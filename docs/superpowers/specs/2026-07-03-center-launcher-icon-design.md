# Center Launcher Icon Design

## Goal

Center the launcher foreground artwork's visible bounds exactly on both axes in the adaptive icon viewport.

## Current behavior

The foreground PNG is horizontally centered, while its visible bounds sit above the source canvas center. The current adaptive-icon insets additionally move the artwork upward and rightward.

## Design

Change only `app/src/main/res/drawable/ic_launcher_foreground.xml`:

- Use equal left and right insets so the horizontally centered source remains centered.
- Use calculated top and bottom insets that move the source artwork downward until its visible vertical bounds share the viewport center.
- Keep every density-specific PNG unchanged to avoid raster resampling and visual drift.

No application logic, dependencies, or legacy launcher bitmap resources change.

## Verification

1. Measure the transformed foreground bounds and confirm their center matches the adaptive icon viewport center on both axes.
2. Compile Android resources with the repository's Gradle validation command.
3. Install a fresh debug build on an emulator, open the launcher, and capture a screenshot showing the installed icon.

## Acceptance criteria

- The visible foreground artwork bounds are centered on both axes.
- Android resources compile successfully.
- An emulator launcher screenshot is provided for visual confirmation.
