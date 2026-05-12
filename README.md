# Akachan

Akachan is a native Android baby tracker for parents of infants. It tracks breastfeeding sessions, sleep records, allergies, and baby profile data locally. An optional partner-sharing mode syncs a read-only snapshot to Firebase Firestore so a second device can view current feeding and sleep data.

Current app version: `1.25.0` (`versionCode` 42).

## Features

- Breastfeeding tracking with start, stop, side switch, pause, resume, active timer, and history.
- Breastfeeding notifications for active sessions, per-breast timing, total-feed limits, pause/resume, and stop actions.
- Sleep tracking with active sleep records, history, sleep limit notifications, and schedule generation.
- Baby profile onboarding with name, birth date, and allergy settings.
- Settings for feed timing limits, app preferences, sharing setup, and debug-only design system preview.
- Partner sharing with anonymous Firebase Auth, 8-character share codes, Firestore snapshots, partner management, and a read-only partner dashboard.
- Material 3 Compose UI with the custom Akachan theme, light/dark support, and one-handed primary flows.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose BOM 2026.03.00, Material 3 |
| Navigation | Compose Navigation 2.9.7 |
| Dependency injection | Hilt 2.59 with KSP 2.3.6 |
| Async | Kotlin Coroutines 1.9.0 and Flow |
| Local database | Room 2.8.4 |
| Preferences | DataStore 1.1.1 |
| Sharing | Firebase BOM 33.7.0, Firestore KTX, Auth KTX |
| Testing | JUnit 5, MockK, Turbine, Compose UI Test |
| Quality | ktlint, detekt, Kover |

Authoritative dependency versions live in `gradle/libs.versions.toml`.

## Requirements

- JDK 17
- Android SDK 35
- Android Studio compatible with AGP 9.1.0
- Firebase `google-services.json` in `app/` for partner sharing builds

SDK targets:

| Setting | Value |
| --- | --- |
| `compileSdk` | 35 |
| `minSdk` | 26 |
| `targetSdk` | 35 |
| JVM toolchain | 17 |

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

Formatting and static analysis:

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew detekt
```

Coverage verification uses Kover with a 60 percent minimum line coverage rule:

```bash
./gradlew koverVerify
```

## Architecture

Akachan uses a single-module clean architecture with unidirectional data flow:

```text
Compose screens -> ViewModels -> Use cases -> Repositories -> Room/DataStore/Firebase
       ^                                                         |
       +---------------- StateFlow / Flow updates ---------------+
```

Core rules:

- Domain models are pure Kotlin data classes.
- Repository interfaces live in `domain/repository`; implementations live in `data/repository`.
- Use cases are single-purpose classes with `operator fun invoke`.
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
|-- manager/             # AlarmManager notification schedulers/coordinators
|-- receiver/            # BroadcastReceivers for notification actions
|-- sharing/             # Firebase partner sharing feature
|-- ui/                  # Compose screens, ViewModels, reusable components, theme
`-- util/                # Date/time, Flow, notification, update helpers
```

Important routes:

- `onboarding`
- `home`
- `breastfeeding`
- `breastfeeding/history`
- `sleep`
- `sleep/history`
- `sleep/schedule`
- `settings`
- `design_system/preview`
- `connect_partner`
- `partner_dashboard`
- `manage_sharing`

## Data Storage

Local Room database: `baby_tracker_db`

- `breastfeeding_sessions`
  - `start_time`, `end_time`, `starting_side`, `switch_time`, `notes`
  - `paused_at`, `paused_duration_ms`
- `sleep_records`
  - `start_time`, `end_time`, `sleep_type`, `notes`

DataStore preferences:

- Baby name and birth date
- Allergies
- Onboarding state
- Breastfeeding timing limits
- Sharing mode and share metadata

## Partner Sharing

Sharing is optional and disabled by default. Normal tracking works offline and stores data locally.

Primary device flow:

1. Anonymous Firebase sign-in.
2. Generate an 8-character uppercase share code.
3. Write `ShareSnapshot` to `shares/{shareCode}`.
4. Sync recent baby, breastfeeding, and sleep data after meaningful tracking actions.

Partner device flow:

1. Enter share code.
2. Anonymous Firebase sign-in.
3. Register partner UID in `shares/{shareCode}/partners/{uid}`.
4. Read `ShareSnapshot` and show `PartnerDashboardScreen` in read-only mode.

Firestore-bound data must use snapshot models from `sharing/domain/model`. Do not write Room entities directly to Firestore.

## Notifications

Breastfeeding and sleep notifications use `AlarmManager`, dedicated schedulers, and action receivers.

- `BreastfeedingSessionNotificationCoordinator` manages active breastfeeding notification lifecycle.
- `BreastfeedingNotificationManager` schedules max-total and per-breast alarms.
- `SleepNotificationManager` schedules sleep limit alarms.
- `NotificationHelper` builds themed notification layouts and action intents.

Required Android permissions include `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `USE_EXACT_ALARM`.

## Specs And Docs

- `AGENTS.md` and `CLAUDE.md`: project working instructions and architecture summary.
- `PRODUCT.md`: product principles and target user context.
- `DESIGN.md`: design direction.
- `specs/`: detailed feature specs, including app structure and partner sharing.
- `docs/`: design system and planning notes.

## License

This project is licensed under the [MIT License](LICENSE).
