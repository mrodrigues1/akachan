# Feature map

Per-feature jump table. Paths are under `app/src/main/java/com/babytracker/`. Layering: `ui/<feature>/` → `domain/repository/*` + `domain/usecase/<feature>/` → `data/repository/*Impl` → `data/local/` (Room, `BabyTrackerDatabase`).

Feature design docs: `specs/SPEC-00x` for the early features; everything from ~May 2026 on lives in `docs/superpowers/specs/` (dated files — search by feature keyword).

## Features

| Feature | Screens | ViewModels | Repositories | Room (table / DAO) | Use cases / notes |
|---------|---------|------------|--------------|--------------------|--------------------|
| breastfeeding | `BreastfeedingScreen`, `BreastfeedingHistoryScreen`, `FeedSettingsScreen` | `BreastfeedingViewModel`, `FeedSettingsViewModel` | `BreastfeedingRepository`, `FeedSettingsRepository` | `breastfeeding_sessions` / `BreastfeedingDao` | `domain/usecase/breastfeeding/` (prediction, edit validation, pause/resume/switch side). Feed-prediction settings live here, not in `feeding` |
| bottlefeed | `BottleFeedScreen`, `BottleFeedSheet` | `BottleFeedViewModel` | `BottleFeedRepository` | `bottle_feeds` / `BottleFeedDao` | `domain/usecase/bottlefeed/` (log/edit/delete) |
| feeding | `UnifiedFeedingHistoryScreen` | `FeedingHistoryViewModel` | — (aggregates breast + bottle) | none | `domain/usecase/feeding/` (merged history/summary) |
| pumping | `PumpingScreen`, `PumpingHistoryScreen` | `PumpingViewModel`, `PumpingHistoryViewModel` | `PumpingRepository` | `pumping_sessions` / `PumpingDao` | `domain/usecase/pumping/` (start/stop/pause/resume, edit validation) |
| inventory (milk stash) | `InventoryScreen`, `InventorySettingsScreen`, `AddBagSheet` | `InventoryViewModel`, `InventorySettingsViewModel` | `InventoryRepository`, `InventorySettingsRepository` | `milk_bags` / `MilkBagDao` | `domain/usecase/inventory/` (bags, expiration) |
| sleep | `SleepTrackingScreen`, `SleepHistoryScreen`, `SleepScheduleScreen`, `SleepSettingsScreen` | `SleepViewModel`, `SleepSettingsViewModel` | `SleepRepository`, `SleepSettingsRepository`, `SleepRecommendationRepository` | `sleep_records` / `SleepDao`; recommendations / `SleepRecommendationDao` | `domain/usecase/sleep/` (save/update/stop, schedule, prediction); math in `domain/sleep/` |
| diaper | `DiaperScreen`, `DiaperHistoryScreen`, `DiaperSheet` | `DiaperViewModel`, `DiaperHistoryViewModel` | `DiaperRepository` | `diaper_changes` / `DiaperDao` | `domain/usecase/diaper/` |
| vaccine | `VaccineDashboardScreen`, `VaccineHistoryScreen`, `VaccineSettingsScreen`, `VaccineSheet` | `VaccineViewModel`, `VaccineDashboardViewModel`, `VaccineHistoryViewModel`, `VaccineSettingsViewModel` | `VaccineRepository`, `VaccineSettingsRepository` | `vaccines` / `VaccineDao` | `domain/usecase/vaccine/` (scheduled→administered lifecycle). Not partner-synced |
| doctorvisit | `DoctorVisitDashboardScreen`, `DoctorVisitScreen` (`?visitId=`), `DoctorVisitHistoryScreen`, `DoctorVisitSettingsScreen`, `VisitQuestionsScreen` | `DoctorVisit*ViewModel` ×4, `VisitQuestionsViewModel` | `DoctorVisitRepository`, `DoctorVisitSettingsRepository` | `doctor_visits` + `visit_questions` (NULL visit_id = inbox) / `DoctorVisitDao` | `domain/usecase/doctorvisit/` (visits, question inbox, snapshot attach) |
| growth | `GrowthScreen`, `AddMeasurementSheet` | `GrowthViewModel` | `GrowthRepository` | `growth_measurements` / `GrowthMeasurementDao` | `domain/usecase/growth/`; WHO percentiles in `domain/growth/` + `data/growth/AssetWhoReferenceData` |
| milestone | `MilestonesScreen`, `MilestoneDetailScreen`, `MilestoneEditorSheet` | `MilestonesViewModel`, `MilestoneDetailViewModel` | `MilestoneRepository` | `milestones` / `MilestoneDao` | No usecase package; photo helpers `MilestonePhoto*` |
| trends | `TrendsScreen` | `TrendsViewModel` | — (aggregates feed + sleep) | none | `domain/usecase/trends/`, models in `domain/trends/` |
| home | `HomeScreen`, `HomeTileContent` | `HomeViewModel` | — (aggregates all) | none | `HomeTile` enum is the canonical tile set (see AI_REPO_MAP common tasks) |
| onboarding | `OnboardingScreen` + `onboarding/components/` step composables | `OnboardingViewModel` | `BabyProfileRepository`, `BabyRepository` | `babies` / `BabyProfileDao` | `domain/usecase/baby/`; SPEC-002 (allergies live here) |
| settings | `SettingsScreen`, `DataSection`, `AppLocale` | `SettingsViewModel`, `DataExportViewModel` | `SettingsRepository` (DataStore) | none | Theme (SPEC-003), export/backup entry points |
| features (toggles) | `FeaturesScreen`, `FeaturePicker` | `FeaturesViewModel` | `FeatureToggleRepository` | none | `domain/usecase/features/`; `FeatureSuppressionCoordinator` mutes notifications of disabled domains |
| sharing (owner side) | `ConnectPartnerScreen`, `ManageSharingScreen` | `ConnectPartnerViewModel`, `ManageSharingViewModel` | top-level `sharing/` package (Firebase, no Room) | none | SPEC-005; `sharing/data/firebase/FirestoreSharingService`, `FirestoreSnapshotMapping`; sync/connect/revoke use cases; `work/PartnerOpDrainWorker` |
| partner (partner-mode UI) | `PartnerDashboardScreen`, `PartnerFeedHistoryScreen`, `PartnerSleepHistoryScreen` | `Partner*ViewModel` ×5 | consumes `sharing/` | none | SPEC-007 (partner bottle feeds); partner feed/sleep ops via `sharing/usecase/` |

## Shared UI packages

- `ui/common/` — form primitives (`DateTimeFieldRow`, `FieldAccent`, `PickerAccentTheme`)
- `ui/component/` — reusable composables: history cards, edit/delete menus, empty states, labels, `TimerDisplay`, `SideSelector`, `SheetSaveButton`, `DeleteConfirmationDialog`
- `ui/theme/` — Material theme + per-feature palettes (`DiaperPalette`, `GrowthPalette`, `VaccinePalette`, `DoctorVisitPalette`, `MilestonePalette`), `DesignSystemPreviewScreen`

## Navigation

`navigation/Routes.kt` (constants) + `navigation/AppNavGraph.kt` (graph, split into `insightsGraph`, `settingsGraph`, `pumpingGraph`, `doctorVisitGraph`). Start destination: PARTNER mode → `PARTNER_DASHBOARD`; onboarded → `HOME`; else `ONBOARDING`. Route values follow `feature` / `feature/history` / `feature/settings`; parameterized: `milestones/{milestoneId}`, `doctor_visit?visitId={visitId}`.

## Cross-cutting singletons (`manager/`, SPEC-004)

- Session controllers: `SleepSessionController`, `BreastfeedingSessionController`
- Notifications, per domain: `*NotificationManager` / `*Scheduler` / `*Coordinator` for sleep (incl. `NapReminder*`, `PredictiveSleep*`, `StaleSleepNotificationCanceller`), breastfeeding (`PredictiveFeed*`), inventory (`StashExpiration*`), doctor visit + vaccine reminders, partner feeds
- App-wide: `NotificationScheduler`, `NotificationPermissionChecker`, `FeatureSuppressionCoordinator`
