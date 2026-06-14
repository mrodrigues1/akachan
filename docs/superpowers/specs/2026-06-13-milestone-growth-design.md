# Milestone Tracker & Growth Tracking — Design Spec

- **Date:** 2026-06-13
- **App:** BabyTracker (Akachan)
- **Status:** Draft for implementation planning
- **Audience:** 0–12 month infants

## 1. Summary

Add two cohesive, WHO-based sub-features to the app, shipped under one Linear project:

1. **Growth tracking** — log weight, length, and head-circumference measurements over time and plot them against WHO Child Growth Standards percentile curves.
2. **Milestone tracker** — record achievement of the WHO six gross-motor milestones, each shown with its typical window of achievement, plus an optional photo.

Both integrate with the existing Home screen tiles and the read-only partner-sharing snapshot.

## 2. Goals / Non-goals

**Goals**

- Log growth measurements (weight / length / head circumference) with date and optional note.
- Show WHO percentile curves and the baby's plotted measurements + computed percentile rank.
- Log the WHO six gross-motor milestones with achievement date, optional photo, optional note.
- Show each milestone's typical window of achievement (informational).
- Metric / imperial unit toggle.
- Home-screen tiles for both features (reorderable).
- Include latest growth + milestone data in the partner-sharing snapshot (read-only).
- Include both in local backup/export.

**Non-goals (YAGNI for v1)**

- Overdue milestone reminders / notifications.
- CDC growth charts (WHO only; CDC itself recommends WHO under age 2).
- Custom user-defined milestones.
- Derived metrics: BMI-for-age, weight-for-length, head-circ-for-length.
- Multi-baby support.
- Syncing milestone photos to Firestore (photos stay local).

## 3. Prerequisite — Baby sex

WHO growth curves are sex-specific, and the app currently stores no sex/gender for the baby. This must land first.

- Add `BabySex { MALE, FEMALE, UNSPECIFIED }` to the DataStore-backed baby profile.
- Capture during onboarding and allow editing in settings.
- When `UNSPECIFIED`: measurements can still be logged and raw values shown, but percentile **rank** is suppressed with a "set sex to see percentiles" call-to-action. Curves that require a sex are hidden until sex is set.

## 4. Data model

Room database moves from version 9 to 10 with a single migration adding two tables.

```
GrowthMeasurementEntity
  id: Long (PK, autoGen)
  takenAtEpochMs: Long
  type: String            // WEIGHT | LENGTH | HEAD_CIRC
  valueMicros: Long       // canonical: grams for WEIGHT, millimetres for LENGTH/HEAD_CIRC
  notes: String?

MilestoneAchievementEntity
  id: Long (PK, autoGen)
  milestone: String       // Milestone enum name (one row per achieved milestone)
  achievedOnEpochDay: Long
  photoUri: String?
  notes: String?
```

Domain models (pure Kotlin, no framework imports):

- `GrowthMeasurement(id, takenAt: Instant, type: GrowthType, value: <canonical>, notes)`
- `GrowthType { WEIGHT, LENGTH, HEAD_CIRC }`
- `MilestoneAchievement(id, milestone: Milestone, achievedOn: LocalDate, photoUri, notes)`
- `Milestone` — enum of the WHO six gross-motor milestones, each carrying its window-of-achievement month range as data:
  - `SITTING_WITHOUT_SUPPORT` (≈3.8–9.2 mo)
  - `HANDS_AND_KNEES_CRAWLING` (≈5.2–13.5 mo)
  - `STANDING_WITH_ASSISTANCE` (≈4.8–11.4 mo)
  - `WALKING_WITH_ASSISTANCE` (≈5.9–13.7 mo)
  - `STANDING_ALONE` (≈6.9–16.9 mo)
  - `WALKING_ALONE` (≈8.2–17.6 mo)

Entity ↔ domain conversion via extension functions (no Mapper classes).

## 5. WHO reference data

- Bundle WHO LMS tables as JSON assets under `app/src/main/assets/who/`:
  - weight-for-age, length-for-age, head-circumference-for-age
  - male and female
  - ages 0–24 months by month (covers the audience with headroom)
- Each entry: `{ ageMonths, L, M, S }`.
- Loaded once into memory on first use.
- `WhoPercentileCalculator` — pure Kotlin, zero framework imports:
  - `zScore(value, l, m, s) = (((value / m).pow(l)) - 1) / (l * s)` (with the `l == 0` log fallback)
  - `percentile(z)` via the standard normal CDF
  - `curvePoints(type, sex)` returning the P3/P15/P50/P85/P97 curves for charting
- Fully unit-testable; verified with golden values against published WHO reference points.

## 6. Units

- Single `MeasurementSystem { METRIC, IMPERIAL }` preference in DataStore, mirroring the existing `VOLUME_UNIT` pattern in `SettingsRepository`.
- Storage stays canonical (grams, millimetres); conversion and formatting happen at the UI edge via extension functions in `util/`.
- Display: weight as kg or lb-oz; length and head circumference as cm or in.
- Settings screen gains the toggle.

## 7. Use cases

`domain/usecase/growth/`:

- `AddGrowthMeasurementUseCase`
- `GetGrowthHistoryUseCase`
- `GetGrowthChartDataUseCase` — measurements + WHO curves + computed percentile for the active type/sex
- `DeleteGrowthMeasurementUseCase`

`domain/usecase/milestone/`:

- `LogMilestoneUseCase`
- `GetMilestoneProgressUseCase` — full catalog joined with achievements and windows
- `DeleteMilestoneUseCase`

Each is a single-responsibility class with `suspend operator fun invoke(...)`, injecting the repository interface.

## 8. UI / navigation

Two new routes, each following the `*Screen` + `*ViewModel` + `*UiState` pattern.

- **GrowthScreen**: latest stats summary; per-metric tabs (weight / length / head); percentile chart drawn with Compose `Canvas` (no chart library dependency); scrollable history list; add-measurement bottom sheet; "set sex" CTA when sex is unspecified.
- **MilestonesScreen**: the six WHO milestones as cards showing either the achieved date or a "log" affordance plus the typical window; optional photo thumbnail; log/edit bottom sheet with a photo picker.
- Add `GROWTH` and `MILESTONES` to the `HomeTile` enum, appended to `DEFAULT_ORDER`. `reconcile()` already appends new tiles for existing users automatically.

## 9. Partner sharing

- Extend `ShareSnapshot` with `growth: List<GrowthSnapshot> = emptyList()` and `milestones: List<MilestoneSnapshot> = emptyList()` (default-empty preserves back-compat).
- Update `DomainToSnapshot` to populate them and the partner dashboard to render them read-only.
- Photos are **not** synced — Firestore holds metadata only; photos stay on-device.

## 10. Backup / export

- Bump `CURRENT_BACKUP_FORMAT_VERSION` from 1 to 2.
- Add `growth: List<GrowthBackup>` and `milestones: List<MilestoneBackup>` to `BackupData`; add `sex` to `BabyBackup`.
- Restore tolerates v1 backups (missing fields default to empty / unspecified).
- Milestone photo files are included in the backup archive if feasible; otherwise metadata-only (decided within that issue).

## 11. Testing

- Use cases and ViewModels: JUnit 5 + MockK + Turbine.
- `WhoPercentileCalculator`: golden-value tests against published WHO reference percentiles.
- Room migration 9 → 10: migration test.
- Screens: Robolectric / Compose UI tests for the key flows and the unspecified-sex state.

## 12. Linear project breakdown (one issue ≈ one PR)

| # | Issue | Summary |
|---|-------|---------|
| 1 | Baby sex | Profile field + onboarding/settings capture + unspecified handling |
| 2 | Growth data layer | Entity, DAO, migration 9→10, repository, domain, backup wiring |
| 3 | WHO percentile engine | JSON assets + `WhoPercentileCalculator` + golden tests |
| 4 | Units | `MeasurementSystem` pref + format/parse + settings toggle |
| 5 | Growth UI | Screen, VM, Canvas chart, add sheet, route, Home tile |
| 6 | Milestone data layer | Entity, DAO, repository, domain, catalog + windows, backup wiring |
| 7 | Milestone UI | Screen, VM, log/edit, photo picker, route, Home tile |
| 8 | Partner sharing | Snapshot fields + DomainToSnapshot + dashboard render |
| 9 | Polish / integration | Empty states, accessibility, end-to-end backup/restore verification |

**Dependencies:** 1 → {2, 6}; 2 → {3, 5}; 3 → 5; 4 → 5; 6 → 7; {5, 7} → 8.

**Suggested order:** 1, 2, 3, 4, 5, 6, 7, 8, 9.
