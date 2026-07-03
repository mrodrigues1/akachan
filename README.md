# Akachan

Akachan is a native Android baby tracker for parents of infants (0–12 months). It tracks feeding (breast, bottle, pumping), a frozen milk stash, sleep, diapers, growth, milestones, vaccines, doctor visits, and allergies — all stored locally. An optional partner-sharing mode syncs data to Firebase Firestore so a second device can follow along and log feeds and sleep.

## Features

- Breastfeeding sessions with timers, side switching, pause/resume, and next-feed prediction.
- Bottle feeds, pumping sessions, and a milk-stash inventory with expiration reminders.
- Sleep tracking with schedule generation, sleep-window prediction, and reminders.
- Diaper changes, growth measurements with WHO percentile charts, and milestones with photos.
- Vaccine records (scheduled → administered lifecycle) and doctor visits with a question inbox.
- Trends dashboard, home-screen widgets (Glance), and Quick Settings tiles for feed/sleep.
- Data backup/restore (JSON), CSV export, and a PDF report.
- Partner sharing via anonymous Firebase Auth and 8-character share codes: partner dashboard, partner-logged bottle feeds, and shared sleep control.
- Localized in English and Brazilian Portuguese. Material 3 Compose UI with the custom Akachan theme, light/dark support, and one-handed primary flows.

## Tech Stack

Kotlin, Jetpack Compose (Material 3), Hilt (KSP), Room, DataStore, Coroutines/Flow, WorkManager, Glance, Firebase (Firestore + anonymous Auth, sharing only), Vico charts, Kotlinx Serialization.

Authoritative dependency versions live in `gradle/libs.versions.toml`. App version lives in `app/build.gradle.kts`.

## Requirements

- JDK 17
- Android SDK (compileSdk 36, minSdk 26, targetSdk 35)
- Android Studio compatible with AGP 9.1
- Firebase `google-services.json` in `app/` for partner sharing builds

## Setup

```bash
git clone https://github.com/matheusr/akachan.git
cd akachan
```

Open the project in Android Studio as a Gradle project, select the `app` configuration, then run on an emulator or device.

CLI install:

```bash
./gradlew installDebug
```

## Build

```bash
./gradlew assembleDebug
./gradlew build
./gradlew bundleRelease
```

Release signing reads these environment variables:

- `SIGNING_KEYSTORE` optional path, defaults to `app/release.keystore`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Release builds enable R8/ProGuard. If signing variables are not present, the release signing config is not attached.

## Test And Quality

Unit tests:

```bash
./gradlew test
```

Instrumentation tests:

```bash
./gradlew connectedAndroidTest
```

Formatting and static analysis (also run by the pre-commit hook):

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Coverage verification uses Kover with a 60 percent minimum line coverage rule:

```bash
./gradlew koverVerify
```

## Architecture

Akachan uses a single-module clean architecture with unidirectional data flow:

```text
Compose screens -> ViewModels -> (use cases, where behaviour exists) -> Repositories -> Room/DataStore/Firebase
       ^                                                                    |
       +------------------------ StateFlow / Flow updates -----------------+
```

Core rules:

- Domain models are pure Kotlin data classes.
- Repository interfaces live in `domain/repository`; implementations live in `data/repository`.
- Use cases are single-purpose classes with `operator fun invoke`, written only when they contain behaviour (ADR-0001) — plain CRUD calls repositories directly from ViewModels.
- Entity/domain conversions are extension functions. No mapper classes.
- No base ViewModel, base Fragment, or sealed `Result<T>` wrappers for normal use-case returns.
- Firebase access stays inside the sharing layer and `di/SharingModule.kt`.

## Project Structure

```text
app/src/main/java/com/babytracker/
|-- BabyTrackerApp.kt
|-- MainActivity.kt
|-- navigation/          # Routes and AppNavGraph
|-- di/                  # Hilt modules
|-- domain/              # Models, repository interfaces, use cases
|-- data/                # Room, DataStore-backed repositories
|-- manager/             # Notification schedulers/coordinators, session controllers
|-- receiver/            # BroadcastReceivers for notification actions
|-- sharing/             # Firebase partner sharing feature
|-- export/              # Backup export/import, CSV, PDF report
|-- tile/                # Quick Settings tiles
|-- widget/              # Glance home-screen widgets
|-- ui/                  # Compose screens, ViewModels, reusable components, theme
|-- util/                # Date/time, Flow, notification, update helpers
`-- debug/               # Debug-build data seeding
```

Routes live in `navigation/Routes.kt`; a per-feature map of screens, ViewModels, and data classes is in `docs/AI_FEATURE_MAP.md`.

## Data Storage

- Local Room database `baby_tracker_db` — one table per tracked domain (feeds, sleep, pumping, milk bags, diapers, growth, milestones, vaccines, doctor visits, baby profile). Versioned schema JSONs live in `app/schemas/`.
- DataStore preferences — baby profile details, onboarding state, feature toggles, per-feature settings, sharing metadata.
- Changing the persisted model ripples into backup, CSV, and partner sync — see `docs/AI_DATA_CHANGE_CHECKLIST.md`.

## Partner Sharing

Sharing is optional and disabled by default. Normal tracking works offline and stores data locally.

Primary device flow:

1. Anonymous Firebase sign-in.
2. Generate an 8-character uppercase share code.
3. Write `ShareSnapshot` to `shares/{shareCode}`.
4. Sync tracked data after meaningful tracking actions.

Partner device flow:

1. Enter share code.
2. Anonymous Firebase sign-in.
3. Register partner UID in `shares/{shareCode}/partners/{uid}`.
4. Read `ShareSnapshot` and show the partner dashboard; partner-logged feeds and sleep write back as ops that the owner device applies.

Firestore-bound data must use snapshot models from `sharing/domain/model`. Do not write Room entities directly to Firestore. Firestore security rules live in `firestore.rules` and deploy manually — see `firebase/README.md`.

## Notifications

Notifications use `AlarmManager`/WorkManager, dedicated schedulers, and action receivers — per-domain managers cover active feed/sleep sessions, predictive feed/sleep reminders, nap reminders, stash expiration, vaccine and doctor-visit reminders (see `docs/adr/0005` and `manager/`).

Required Android permissions include `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `USE_EXACT_ALARM`.

## Specs And Docs

- `AGENTS.md`: canonical working instructions (CLAUDE.md and QWEN.md import it).
- `PRODUCT.md`: product principles and target user context.
- `DESIGN.md`: the design system (palettes, typography, components).
- `CONTEXT.md`: domain glossary; `docs/adr/`: architecture decision records.
- `specs/` and `docs/superpowers/specs/`: feature specs (early core specs in the former, later features in the latter).
- `docs/AI_REPO_MAP.md`, `docs/AI_FEATURE_MAP.md`, `docs/AI_DATA_CHANGE_CHECKLIST.md`: agent navigation maps.

## License

This project is licensed under the [MIT License](LICENSE).
