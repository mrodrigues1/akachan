# CLAUDE.md — BabyTracker (Akachan)

A native Android baby tracking app for parents of infants (0–12 months). Tracks breastfeeding sessions, sleep patterns, and allergies. Core tracking data is stored locally. An optional partner-sharing feature syncs a read-only snapshot to Firebase Firestore via anonymous Firebase Auth.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose (BOM 2026.03.00), Material 3 |
| Navigation | Compose Navigation 2.9.7 |
| DI | Hilt 2.59 (KSP 2.3.6) |
| Async | Kotlin Coroutines 1.9.0 + Flow |
| Local DB | Room 2.8.4 |
| Preferences | DataStore 1.1.1 |
| Sharing | Firebase BOM 33.7.0 (Firestore KTX, Auth KTX) |
| Widgets | Glance 1.1.1 (appwidget + material3) |
| Background work | WorkManager 2.10.0 |
| Min SDK | 26 | Target SDK 35 | JVM 17 |
| Testing | JUnit 5, MockK 1.13.13, Turbine 1.2.0, Compose UI Test |

> Authoritative versions: `gradle/libs.versions.toml`. Update this table whenever that file changes.

---

## Architecture

Three-layer clean architecture with unidirectional data flow:

```
UI Layer       Compose Screens → ViewModels → StateFlow<*UiState>
                    ↑                               |
Domain Layer   Use Cases ← ViewModels              |
                    ↓                               |
Data Layer     Repository Impls → Room DAOs / DataStore
                                        ↓
                                  Flow<T> bubbles up
```

**Key principles (from `specs/SPEC-001-APP-STRUCTURE.md`):**
- SOLID: depend on abstractions (repository interfaces), single-responsibility use cases
- KISS: flat packages, no mapper classes, no base classes, no sealed `Result<>` wrappers
- DDD: domain models are pure Kotlin data classes — zero framework imports
- UI: Material 3 with custom "Baby" palette, rounded shapes, and one-handed usability

**Anti-goals:** no multi-module setup, no `Mapper` classes, no `BaseViewModel`, no `BaseFragment`.

---

## Package Structure

```
app/src/main/java/com/babytracker/
├── BabyTrackerApp.kt          # @HiltAndroidApp Application class
├── MainActivity.kt            # Single activity, edge-to-edge Compose host
├── navigation/
│   ├── AppNavGraph.kt         # 12 routes: onboarding, home, breastfeeding,
│   │                          #   breastfeeding_history, sleep, sleep_history,
│   │                          #   sleep_schedule, settings, design_system_preview,
│   │                          #   connect_partner, partner_dashboard, manage_sharing
│   └── Routes.kt              # Route string constants object
├── di/
│   ├── DatabaseModule.kt      # Provides Room DB + DAOs
│   ├── DataStoreModule.kt     # Provides DataStore<Preferences>
│   ├── RepositoryModule.kt    # Binds core interfaces → implementations
│   ├── NotificationSchedulerModule.kt  # Binds notification schedulers → impls
│   └── SharingModule.kt       # Binds SharingRepository; provides FirebaseFirestore + FirebaseAuth
├── domain/
│   ├── model/                 # Pure data classes: Baby, BreastfeedingSession,
│   │                          #   SleepRecord, SleepSchedule, ThemeConfig, UpdateInfo
│   │                          # Enums: AllergyType, BreastSide, SleepType
│   ├── repository/            # Interfaces: BabyRepository, BreastfeedingRepository,
│   │                          #   SleepRepository, SettingsRepository
│   └── usecase/
│       ├── baby/              # GetBabyProfile, SaveBabyProfile
│       ├── breastfeeding/     # StartSession, StopSession, GetHistory, SwitchSide,
│       │                      #   PauseSession, ResumeSession
│       └── sleep/             # StartRecord, StopRecord, GetHistory, GenerateSchedule
├── data/
│   ├── local/
│   │   ├── BabyTrackerDatabase.kt   # Room DB v2, entities: breastfeeding_sessions, sleep_records
│   │   ├── dao/               # BreastfeedingDao, SleepDao
│   │   ├── entity/            # BreastfeedingEntity, SleepEntity
│   │   └── converter/         # TypeConverter: Instant ↔ Long (epoch ms)
│   └── repository/            # BabyRepositoryImpl, BreastfeedingRepositoryImpl,
│                              #   SleepRepositoryImpl, SettingsRepositoryImpl
├── manager/
│   ├── NotificationScheduler.kt              # Interface: schedule/cancel AlarmManager alarms (breastfeeding)
│   ├── BreastfeedingNotificationManager.kt   # Impl: schedules max-time and per-breast alarms
│   ├── BreastfeedingSessionNotificationCoordinator.kt  # Orchestrates full session notification lifecycle
│   ├── SleepNotificationScheduler.kt         # Interface: schedule/cancel sleep alarms
│   └── SleepNotificationManager.kt           # Impl: schedules sleep limit alarms
├── receiver/
│   ├── BreastfeedingActionReceiver.kt   # @AndroidEntryPoint BroadcastReceiver for session actions
│   ├── BreastfeedingNotificationReceiver.kt  # @AndroidEntryPoint BroadcastReceiver for alarm triggers
│   └── SleepActionReceiver.kt           # @AndroidEntryPoint BroadcastReceiver for sleep actions
├── sharing/
│   ├── data/
│   │   ├── firebase/          # FirestoreSharingService.kt (Firestore + Auth operations)
│   │   └── repository/        # SharingRepositoryImpl.kt
│   ├── domain/
│   │   ├── model/             # AppMode.kt (enum: NONE/PRIMARY/PARTNER),
│   │   │                      #   ShareSnapshot.kt, BabySnapshot.kt,
│   │   │                      #   SessionSnapshot.kt, SleepSnapshot.kt,
│   │   │                      #   PartnerInfo.kt, ShareCode.kt,
│   │   │                      #   DomainToSnapshot.kt (extension fns)
│   │   └── repository/        # SharingRepository.kt (interface)
│   └── usecase/               # ConnectAsPartnerUseCase, FetchPartnerDataUseCase,
│                              #   GenerateShareCodeUseCase, RevokePartnerUseCase,
│                              #   SyncToFirestoreUseCase
├── ui/
│   ├── onboarding/            # OnboardingScreen + OnboardingViewModel
│   │   └── components/        # Reusable onboarding sub-composables
│   ├── home/                  # HomeScreen + HomeViewModel
│   ├── breastfeeding/         # BreastfeedingScreen, BreastfeedingHistoryScreen + VMs
│   ├── sleep/                 # SleepTrackingScreen, SleepHistoryScreen, SleepScheduleScreen + VMs
│   ├── settings/              # SettingsScreen + SettingsViewModel
│   ├── partner/               # PartnerDashboardScreen + PartnerDashboardViewModel
│   ├── sharing/               # ConnectPartnerScreen + VM, ManageSharingScreen + VM
│   ├── component/             # Reusable: TimerDisplay, HistoryCard, SideSelector
│   └── theme/                 # Theme.kt, Color.kt, Shape.kt, Type.kt,
│                              #   DesignSystemPreviewScreen.kt (debug catalog)
└── util/
    ├── DateTimeExt.kt         # Instant.formatTime(), formatDateTime(), Duration.formatDuration(),
    │                          #   Duration.formatElapsedAgo()
    ├── FlowExt.kt             # Flow.catchAndLog()
    ├── NotificationHelper.kt  # Cancel/show notification helpers — builds design-system-themed
    │                          #   notifications (per-type small icon + accent color via setColor())
    └── UpdateChecker.kt       # In-app update check utility
```

---

## Theme & UI Tokens

All design-system source lives in `ui/theme/`. The live catalog is at `ui/theme/DesignSystemPreviewScreen.kt` — reachable from Settings → Developer (debug builds only).

### Palette Scale (`Color.kt`)

Three hue families, each with a 4-stop scale. The scale semantics are shared across families:

| Stop | Role |
|------|------|
| 700  | Primary action color |
| 200  | Container / background tint |
| 900  | On-container text (dark) |
| 100  | Softest tone |

| Family | 100 | 200 | 700 | 900 |
|--------|-----|-----|-----|-----|
| **Pink** (Feeding/Primary) | `#F4C2C2` | `#F8BBD0` | `#C2185B` | `#880E4F` |
| **Blue** (Sleep/Secondary) | `#89CFF0` | `#B3E5FC` | `#1976D2` | `#0D47A1` |
| **Green** (Success/Tertiary) | `#90EE90` | `#C8E6C9` | `#388E3C` | `#1B5E20` |
| **Amber** (Warning) | `#FFCC80` | `#FFE0B2` | `#E65100` | `#7A3600` |

Surface grays and yellows have no scale equivalent:
- `SoftYellow` `#FFF9C4` — soft background hint
- `SurfaceYellow` `#FFFDE7` — light scheme surface / background
- `Amber800` `#7A4800` — off-scale raw used only by the dark-scheme warning container

### Semantic Color Tokens (`Color.kt` → `Theme.kt`)

**Light scheme**

| Token name | Palette constant | Hex |
|------------|-----------------|-----|
| `primary` | `Pink700` | `#C2185B` |
| `onPrimary` | `OnPrimaryWhite` | `#FFFFFF` |
| `primaryContainer` | `Pink200` | `#F8BBD0` |
| `onPrimaryContainer` | `Pink900` | `#880E4F` |
| `secondary` | `Blue700` | `#1976D2` |
| `onSecondary` | `OnSecondaryWhite` | `#FFFFFF` |
| `secondaryContainer` | `Blue200` | `#B3E5FC` |
| `onSecondaryContainer` | `Blue900` | `#0D47A1` |
| `tertiary` | `Green700` | `#388E3C` |
| `onTertiary` | `OnTertiaryWhite` | `#FFFFFF` |
| `tertiaryContainer` | `Green200` | `#C8E6C9` |
| `onTertiaryContainer` | `Green900` | `#1B5E20` |
| `surface` / `background` | `SurfaceYellow` | `#FFFDE7` |
| `onSurface` | `OnSurfaceDark` | `#1A1A1A` |
| `onSurfaceVariant` | `OnSurfaceVariantGrey` | `#6D6A64` |
| `surfaceVariant` | `SurfaceVariantLight` | `#F0EDE0` |
| `outline` | `OutlineLight` | `#CAC4D0` |
| `outlineVariant` | `OutlineVariantLight` | `#CAC4D0` |
| `error` | `ErrorLight` | `#B00020` |
| `errorContainer` | `ErrorContainerLight` | `#FFDAD6` |
| `onErrorContainer` | `OnErrorContainerLight` | `#410002` |

**Dark scheme** — all dark tokens have no palette-scale equivalent (independently chosen for contrast):

| Token name | Hex |
|------------|-----|
| `primary` | `#F48FB1` |
| `primaryContainer` | `#880E4F` (Pink900) |
| `secondary` | `#90CAF9` |
| `secondaryContainer` | `#0D47A1` (Blue900) |
| `tertiary` | `#A5D6A7` |
| `tertiaryContainer` | `#1B5E20` (Green900) |
| `surface` / `background` | `#1C1B1F` |
| `onSurface` | `#E6E1E5` |
| `surfaceVariant` | `#2B2930` |
| `outline` | `#938F99` |
| `outlineVariant` | `#49454F` |
| `error` | `#FFB4AB` |
| `errorContainer` | `#93000A` |

### Warning semantic tokens (extended, non-M3)

Accessed as top-level `val`s from `ui/theme/Color.kt` — **not** wired through `MaterialTheme.colorScheme`. Consumed directly by `NotificationHelper` for the Feeding Limit notification.

| Token | Light | Dark |
|------|-------|------|
| `WarningAmber` | `Amber700` `#E65100` | `Amber100` `#FFCC80` |
| `WarningContainerAmber` | `Amber200` `#FFE0B2` | `Amber800` `#7A4800` |
| `OnWarningContainerAmber` | `Amber900` `#7A3600` | `Amber200` `#FFE0B2` |

### Typography (`Type.kt` → `AkachanTypography`)

All 13 M3 slots are defined. Key custom roles:

| Slot | Weight | Size | Usage |
|------|--------|------|-------|
| `displaySmall` | ExtraBold | 36sp | Timer clock display |
| `headlineLarge` | Bold | 32sp | Screen titles |
| `headlineMedium` | Bold | 28sp | Section titles |
| `headlineSmall` | SemiBold | 24sp | Card titles |
| `titleLarge` | SemiBold | 22sp | TopAppBar title |
| `titleMedium` | SemiBold | 16sp | List item primary text |
| `titleSmall` | Medium | 14sp | List item secondary |
| `bodyLarge` | Normal | 16sp | Body / description |
| `bodyMedium` | Normal | 14sp | Secondary body |
| `bodySmall` | Normal | 12sp | Captions |
| `labelLarge` | SemiBold | 14sp | Button labels |
| `labelMedium` | Bold | 12sp | Section headers (UPPERCASE convention) |
| `labelSmall` | Medium | 11sp | Swatch / chip labels |

### Shapes (`Shape.kt` → `AkachanShapes`)

| Slot | Corner radius | Usage |
|------|--------------|-------|
| `extraSmall` | 4dp | Dense chips |
| `small` | 8dp | Input fields, small cards |
| `medium` | 16dp | Main cards |
| `large` | 24dp | Bottom sheets, dialogs |
| `extraLarge` | 50dp | Primary buttons (FAB-like) |

---

## Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| ViewModel | `*ViewModel` | `OnboardingViewModel` |
| UI State | `*UiState` (data class) | `OnboardingUiState` |
| Screen composable | `*Screen` | `OnboardingScreen` |
| Repository interface | `*Repository` | `BreastfeedingRepository` |
| Repository impl | `*RepositoryImpl` | `BreastfeedingRepositoryImpl` |
| Use case | `*UseCase` | `StartBreastfeedingSessionUseCase` |
| Room DAO | `*Dao` | `BreastfeedingDao` |
| Room entity | `*Entity` | `BreastfeedingEntity` |
| Extension functions | Appended to type | `fun Instant.formatTime()` |

---

## Conventional Commits

All commits follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat(breastfeeding): add session pause functionality` |
| `fix` | Bug fix | `fix(sleep): correct duration calculation for night sleep` |
| `docs` | Documentation changes | `docs: update README with setup instructions` |
| `style` | Code style (formatting, missing semicolons, etc.) | `style: reformat code to match kotlin conventions` |
| `refactor` | Code refactoring without feature change | `refactor(repository): extract common db query logic` |
| `perf` | Performance improvements | `perf(ui): optimize recomposition in HomeScreen` |
| `test` | Adding or updating tests | `test(breastfeeding): add unit tests for session duration` |
| `chore` | Build, deps, CI, tooling | `chore: upgrade Room dependency to 2.6.2` |
| `ci` | CI/CD pipeline changes | `ci: add lint check to GitHub Actions` |
| `revert` | Revert previous commit | `revert: revert "feat(sleep): add nap prediction"` |

### Scope (Optional)

Scopes identify the area of the codebase:
- `breastfeeding` — Breastfeeding feature (sessions, history, etc.)
- `sleep` — Sleep tracking feature (records, schedule, etc.)
- `onboarding` — Onboarding flow
- `settings` — Settings screen and preferences
- `ui` — UI components and theme
- `db` — Database schema and migrations
- `repository` — Data layer and repositories
- `usecase` — Use cases and business logic
- `navigation` — Navigation graph and routing
- `di` — Dependency injection and modules
- `sharing` — Partner sharing feature, Firebase sync
- `partner` — Partner dashboard UI
- `notification` — Notification managers, schedulers, coordinators
- `receiver` — BroadcastReceiver classes

### Examples

✅ **Good commits:**
```
feat(breastfeeding): add session duration calculator

Calculates total duration and elapsed time for active sessions.
Handles side switches and provides formatted output.

Closes #42
```

```
fix(sleep): prevent negative duration in sleep records
```

```
test(breastfeeding): add unit tests for StartBreastfeedingSessionUseCase
```

```
docs: add testing conventions to CLAUDE.md
```

❌ **Bad commits:**
```
updated code
```

```
Fix bug in breastfeeding
```

```
wip: work on new feature
```

---

## Workflow

### Branching Model

When working on a new feature, pull `main` branch latest changes and create a new branch from `main` and name it after the feature:
- `feat/breastfeeding-history`
- `fix/sleep-schedule-bug`
- `refactor/settings-screen-refactor`
- `chore/update-room-version`
- `ci/add-android-test-coverage`

---

## Code Patterns

### Use Cases
Single-responsibility classes with `suspend operator fun invoke(...)`:

```kotlin
class StartBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(side: BreastSide): Long { ... }
}
```

No interface abstraction unless multiple implementations exist.

### ViewModels
Expose a single `StateFlow<*UiState>` and event handler functions:

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(...) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState> = ...
    fun onNameChanged(name: String) { ... }
}
```

### Entity ↔ Domain Conversion
Extension functions directly on entity/domain classes — no Mapper classes:

```kotlin
fun BreastfeedingEntity.toDomain(): BreastfeedingSession = ...
fun BreastfeedingSession.toEntity(): BreastfeedingEntity = ...
```

### DateTime
- Always use `java.time.Instant` for timestamps
- Store as epoch milliseconds (Long) in Room and DataStore
- Format via extension functions in `util/DateTimeExt.kt`

---

## Database Schema

**Database:** `baby_tracker_db` (Room v2)

**`breastfeeding_sessions`**
| Column | Type | Notes |
|--------|------|-------|
| `id` | Long PK autoGenerate | |
| `start_time` | Long NOT NULL | epoch ms |
| `end_time` | Long NULLABLE | null = in progress |
| `starting_side` | String NOT NULL | "LEFT" or "RIGHT" |
| `switch_time` | Long NULLABLE | epoch ms when sides switched |
| `notes` | String NULLABLE | |
| `paused_at` | Long NULLABLE | epoch ms when session was paused; null = running |
| `paused_duration_ms` | Long NOT NULL | accumulated paused time in ms (default 0) |

**`sleep_records`**
| Column | Type | Notes |
|--------|------|-------|
| `id` | Long PK autoGenerate | |
| `start_time` | Long NOT NULL | epoch ms |
| `end_time` | Long NULLABLE | null = in progress |
| `sleep_type` | String NOT NULL | "NAP" or "NIGHT_SLEEP" |
| `notes` | String NULLABLE | |

**DataStore (`baby_tracker_prefs`):** baby name, birth date, allergies, onboarding flag, feed timing limits.

---

## Sharing Feature

The optional partner-sharing feature allows a primary user (parent) to share a read-only snapshot of their tracking data with a partner.

### AppMode

`sharing/domain/model/AppMode.kt` defines three modes:

| Value | Meaning |
|-------|---------|
| `NONE` | Sharing not configured; normal app flow |
| `PRIMARY` | This device is the sharing owner |
| `PARTNER` | This device is the read-only partner viewer |

`AppNavGraph` uses `AppMode` to choose the start destination: `PARTNER_DASHBOARD` for `PARTNER` mode, otherwise the normal onboarding/home flow.

### Primary user flow

1. `GenerateShareCodeUseCase` calls `FirestoreSharingService.signInAnonymously()` + `createShareDocument()`.
2. Data changes are synced via `SyncToFirestoreUseCase` (sessions, sleep records, baby info) whenever the primary user completes an action.
3. The share code is an 8-character uppercase alphanumeric string stored in `ShareCode`.

### Partner user flow

1. Partner enters the share code in `ConnectPartnerScreen`.
2. `ConnectAsPartnerUseCase` validates the code and registers the partner UID in the Firestore document.
3. `FetchPartnerDataUseCase` reads the `ShareSnapshot` from Firestore.
4. `PartnerDashboardScreen` presents the data read-only; the partner cannot start or stop sessions.

### Firebase patterns

- `FirestoreSharingService` is `@Singleton`, injected via `SharingModule`.
- Firestore document path: `shares/{shareCode}`. Partner UIDs are stored in a `partners/{uid}` sub-collection.
- Both `FirebaseFirestore` and `FirebaseAuth` instances are provided by `SharingModule` (`@InstallIn(SingletonComponent::class)`).

### Snapshot model

Domain data is converted to the Firestore-friendly snapshot model via extension functions in `DomainToSnapshot.kt` (consistent with the "no Mapper classes" KISS principle):

```kotlin
fun BreastfeedingSession.toSessionSnapshot(): SessionSnapshot = ...
fun SleepRecord.toSleepSnapshot(): SleepSnapshot = ...
```

### What NOT to do in the sharing feature

- Never store raw `BreastfeedingEntity` / `SleepEntity` directly in Firestore — always go through the snapshot model.
- Do not add authenticated (non-anonymous) sign-in without an explicit design decision.
- Do not add sync logic outside of `SyncToFirestoreUseCase` and `FirestoreSharingService`.

---

## Notification Architecture

Notifications are driven by a three-role pattern across `manager/` and `receiver/`.

### Schedulers

Interfaces + implementations that schedule and cancel `AlarmManager`-based alarms:

| Interface | Implementation |
|-----------|---------------|
| `NotificationScheduler` | `BreastfeedingNotificationManager` |
| `SleepNotificationScheduler` | `SleepNotificationManager` |

Both are bound in `NotificationSchedulerModule` and scoped `@Singleton`.

### Coordinator

`BreastfeedingSessionNotificationCoordinator` orchestrates the full lifecycle of breastfeeding session notifications:

- `scheduleInitial()` — called when a session starts
- `showRunning()` / `showPaused()` — updates the ongoing notification
- `rescheduleAfterResume()` — adjusts alarm timing after a pause
- `cancelAllSessionNotifications()` — called on session stop

It reads per-breast and total-feed time limits from `SettingsRepository`.

### Receivers

`receiver/` contains `@AndroidEntryPoint`-annotated `BroadcastReceiver` subclasses:

- `BreastfeedingActionReceiver` — handles notification action intents (switch side, pause, resume, stop)
- `BreastfeedingNotificationReceiver` — handles AlarmManager triggers for breastfeeding time limits
- `SleepActionReceiver` — handles sleep notification action intents

Receivers use `goAsync()` + a `CoroutineScope` so they can safely call `suspend` use cases without blocking the main thread.

### NotificationHelper

`util/NotificationHelper.kt` contains the notification builder logic, notification ID constants, and channel setup. It applies design-system-themed colors (accent `setColor()`) and per-type small icons.

---

## Build & Run

```bash
# Build (assembles debug APK)
./gradlew build

# Unit tests (JUnit 5)
./gradlew test

# Instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Release bundle
./gradlew bundleRelease
```

Build variants: `debug` (default) and `release` (ProGuard minification enabled via `proguard-android-optimize.txt` + `app/proguard-rules.pro`).

### Linux / WSL

The system image ships only a JRE (`java`) — no `javac`. Hilt's annotation processor (`hiltJavaCompileDebug`) requires a full JDK. Gradle downloads one automatically to `~/.gradle/jdks/`. Additionally, the Hilt toolchain property cannot be serialized by the Gradle configuration cache under this setup, so config cache must be disabled on Linux/WSL.

Both are handled via `~/.gradle/gradle.properties` (GRADLE_USER_HOME has higher precedence than the project `gradle.properties`, so this overrides only the local machine and does not affect Windows-side dev):

```properties
# ~/.gradle/gradle.properties
org.gradle.configuration-cache=false
org.gradle.java.home=/home/matheus/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
```

With that file in place, Gradle commands run cleanly without manual env exports or `--no-configuration-cache` flags:

```bash
./gradlew :app:testDebugUnitTest
./gradlew ktlintFormat detekt
./gradlew :app:compileDebugAndroidTestKotlin
```

If the JDK path no longer exists (Gradle re-downloaded a newer version), run `ls ~/.gradle/jdks/` to find the current directory and update `org.gradle.java.home` accordingly.

### Linux / WSL — Test Performance

The architecture tests in `app/src/test/java/com/babytracker/architecture/` use [Konsist](https://github.com/LemonAppDev/konsist) to scan every production Kotlin file. On WSL2, the project lives on the Windows NTFS volume (`/mnt/c/...`) and is read through the 9P/DrvFs bridge, which makes file I/O roughly an order of magnitude slower than native ext4. The first `Konsist.scopeFromProduction()` call inside a test JVM takes **~14 minutes** under that setup vs. seconds on a native disk; subsequent calls in the same JVM reuse the cached parse.

Two patches reduce the impact:

1. **Shared, lazy scope** — `app/src/test/java/com/babytracker/architecture/KonsistScopes.kt` exposes one `productionScope` that the three architecture tests all reuse. Without it, every test method paid the scan cost.
2. **`-PfastTests` opt-out** — all architecture test classes carry `@Tag("architecture")`. `tasks.withType<Test>` reads `-PfastTests` (presence flag — any value enables it) and skips that tag, so the inner dev loop is sub-15-seconds while CI (no flag) still runs the full suite.

Day-to-day commands on WSL:

```bash
# Fast loop — skips the architecture tests (~10s warm, ~4min cold)
./gradlew :app:testDebugUnitTest -PfastTests

# Full suite — run before pushing (matches CI behaviour)
./gradlew :app:testDebugUnitTest
```

When adding new architecture tests, annotate them with `@Tag("architecture")` and use the shared `productionScope` instead of calling `Konsist.scopeFromProduction()` directly. Architecture rules only inspect production sources — test code is intentionally excluded.

For an even bigger speedup, clone the repo onto the WSL ext4 filesystem (e.g. `~/akachan`) and work from there — native filesystem I/O eliminates the 9P bottleneck entirely. The trade-off is that Windows-side tools (Android Studio on Windows, Explorer) can no longer reach the working copy.

### Linux / WSL — UI / Instrumentation Tests

`adb` is not installed in WSL — the Android SDK lives on the Windows side. WSL2 can execute Windows `.exe` files directly, so add the Windows SDK paths to `~/.bashrc`:

```bash
# Android SDK (Windows-side — WSL2 runs .exe directly)
export ANDROID_SDK_ROOT="/mnt/c/Users/mathe/AppData/Local/Android/Sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
```

After `source ~/.bashrc`, `adb` resolves to `adb.exe` on the Windows SDK and connects to the Windows ADB server.

**To run instrumentation tests (`src/androidTest/`) from WSL:**

1. Start the `Pixel_10_Pro` emulator from **Windows** Android Studio (or Windows terminal):
   ```
   # Windows terminal / PowerShell
   %LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Pixel_10_Pro
   ```
2. Wait for boot, then from WSL:
   ```bash
   adb devices          # should list the emulator
   ./gradlew connectedAndroidTest
   ```

**To run Compose UI tests without a device (Robolectric, JVM only):**

Write the test in `src/test/` with `@RunWith(RobolectricTestRunner::class)` and `createComposeRule()`:

```kotlin
@RunWith(RobolectricTestRunner::class)
class MyScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun myTest() {
        composeRule.setContent { MyComposable() }
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
```

Run via: `./gradlew :app:testDebugUnitTest`

> `isIncludeAndroidResources = true` is already set in `testOptions` so resources and assets resolve at JVM test time.

---

## Testing Conventions

Create tests for new features, bug fixes, and edge cases. You do not need to follow strict TDD (test-first); writing tests after the implementation is acceptable. All tests must pass before creating a PR.

After the feature is complete, run all tests. If there are any broken tests, fix them and re-run until all pass.

### Unit Tests (`src/test/`)
- Framework: JUnit 5 (`@Test`, `@BeforeEach`, `runTest`)
- Mocking: MockK — use `mockk()`, `coEvery`, `coVerify`, `slot<T>()` for argument capture
- Flow testing: Turbine — `flow.test { ... }`
- Test class mirrors production class path

```kotlin
class StartBreastfeedingSessionUseCaseTest {
    private lateinit var repository: BreastfeedingRepository

    @BeforeEach
    fun setup() {
        repository = mockk()
    }

    @Test
    fun `invoke saves session and returns id`() = runTest { ... }
}
```

### Instrumentation Tests (`src/androidTest/`)
- Framework: AndroidJUnit4 (JUnit 4 via Compose Test runner)
- Room: use `Room.inMemoryDatabaseBuilder()` — never test against real disk DB
- Compose: use `createComposeRule()` for UI tests

---

## Dependency Injection

Hilt modules live in `di/`:

- **`DatabaseModule`** (`@InstallIn(SingletonComponent::class)`) — provides `BabyTrackerDatabase`, `BreastfeedingDao`, `SleepDao`
- **`DataStoreModule`** (`@InstallIn(SingletonComponent::class)`) — provides `DataStore<Preferences>`
- **`RepositoryModule`** (`@InstallIn(SingletonComponent::class)`) — binds core repository interfaces to implementations with `@Binds @Singleton`
- **`NotificationSchedulerModule`** (`@InstallIn(SingletonComponent::class)`) — binds `NotificationScheduler` → `BreastfeedingNotificationManager`, `SleepNotificationScheduler` → `SleepNotificationManager`
- **`SharingModule`** (`@InstallIn(SingletonComponent::class)`) — binds `SharingRepository` → `SharingRepositoryImpl`; provides `FirebaseFirestore` and `FirebaseAuth` singletons

All repository and service implementations are `@Singleton` scoped.

---

## Specifications

Detailed feature specs live in `specs/`:

- `specs/SPEC-001-APP-STRUCTURE.md` — architecture, package layout, design principles, DB schema

Read specs before implementing new features — they define the intended behaviour and non-goals.

---

## What NOT to Do

- Do not add multi-module structure (everything stays in `:app`)
- Do not create Mapper classes — use extension functions on entity/domain types
- Do not create BaseViewModel or BaseFragment
- Do not wrap return values in `sealed class Result<T>` — let exceptions propagate or use nullable types
- Do not add analytics SDKs or new remote API integrations beyond the existing Firebase sharing feature. Extend the partner-sharing feature only within `sharing/` and `di/SharingModule.kt`.
- Do not use KAPT — KSP is configured for all annotation processing (Hilt, Room)
- Do not access warning tokens (`WarningAmber`, `WarningContainerAmber`, `OnWarningContainerAmber` and their `*Dark` pairs) through `MaterialTheme.colorScheme` — they are extended, non-M3 semantics and ship as top-level `val`s in `ui/theme/Color.kt`. Import them by name.

---

## Code Quality

- This project uses **ktlint** (via `ktlint-gradle` plugin v12.1.1) for Kotlin formatting and **detekt** (v1.23.6) for static analysis.
- The detekt config lives at `config/detekt.yml` and is tuned for Jetpack Compose and MVI (Orbit).
- Formatting is delegated entirely to ktlint; detekt does not enforce formatting rules.
- **Before committing**, always run `./gradlew ktlintFormat` to auto-fix formatting, then `./gradlew detekt` to catch code smells.
- A pre-commit hook is available at `scripts/pre-commit`. Install it with: `cp scripts/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit`
- Both tools run automatically on CI (GitHub Actions) on every push to `main` and on every pull request.
- If detekt reports a violation, **always fix the code - never suppress with `@Suppress`**. Each fix should be its own commit using the format `fix(detekt): fix <RuleName> violations`.
- Useful commands:
  - `./gradlew ktlintCheck` - check formatting
  - `./gradlew ktlintFormat` - auto-fix formatting
  - `./gradlew detekt` - run static analysis
  - `./gradlew detektGenerateConfig` - regenerate the base detekt config (do not overwrite `config/detekt.yml` without review)
