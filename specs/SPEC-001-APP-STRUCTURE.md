# SPEC-001: App Structure & Project Setup

**App Name:** BabyTracker
**Platform:** Native Android
**Status:** Draft
**Version:** 1.0
**Date:** 2026-04-03

---

## 1. Overview

BabyTracker is a native Android application that helps parents track their newborn's breastfeeding sessions, sleep patterns, allergies, and generates evidence-based sleep schedules. The app stores all data locally on the device.

### 1.1 Target Audience

Parents and caregivers of infants aged 0–12 months.

### 1.2 Design Principles

| Principle | Application |
|---|---|
| **SOLID** | Single responsibility per class. Depend on abstractions (repository interfaces). Open for extension via sealed classes/interfaces. |
| **KISS** | Flat package structure. No unnecessary abstractions. Prefer simple `when` expressions over complex inheritance. |
| **DDD** | Domain models are plain Kotlin data classes with no framework dependencies. Business rules live in domain use cases, not in ViewModels or DAOs. |
| **Design Patterns** | Repository pattern for data access. Observer pattern via Kotlin Flows. Factory pattern for schedule generation. Strategy pattern for sleep algorithm modes. |

### 1.3 Anti-Goals (Avoiding Overengineering)

- No multi-module Gradle setup — single module is sufficient for this app size.
- No network layer, API clients, or remote sync.
- No Mapper classes — use extension functions on entities/domain models directly.
- No base classes (BaseViewModel, BaseFragment) — Compose doesn't need them.
- No `sealed class Result<T>` wrapper unless a use case genuinely has multiple outcome types.
- No pagination libraries — data volumes are small (months of daily entries = thousands of rows max).

---

## 2. Technology Stack

| Layer | Technology | Version (min) |
|---|---|---|
| Language | Kotlin | 2.0+ |
| UI Framework | Jetpack Compose | BOM 2024.12+ |
| Design System | Material Design 3 | 1.3+ |
| Architecture | MVVM + Unidirectional Data Flow | — |
| DI | Hilt | 2.52+ |
| Async | Kotlin Coroutines + Flow | 1.9+ |
| Local Storage | Room (SQLite) | 2.6+ |
| Preferences | DataStore (Preferences) | 1.1+ |
| Navigation | Compose Navigation | 2.8+ |
| Testing | JUnit 5, Turbine, MockK, Compose UI Test | — |
| Build | Gradle KTS with Version Catalogs | 8.7+ |

### 2.1 Why Room + DataStore

- **Room** — Relational data: breastfeeding sessions, sleep records, schedule entries. Supports `Flow` queries natively for reactive UI.
- **DataStore (Preferences)** — Simple key-value config: baby profile (name, birth date), allergy flags, breastfeeding timer settings. No schema migration needed for flat preferences.

---

## 3. Architecture

### 3.1 Layer Diagram

```
┌─────────────────────────────────────────────┐
│                 UI Layer                     │
│  Compose Screens → ViewModels → UI State    │
├─────────────────────────────────────────────┤
│               Domain Layer                   │
│  Use Cases ← Domain Models → Interfaces     │
├─────────────────────────────────────────────┤
│                Data Layer                    │
│  Repositories → Room DAOs / DataStore       │
└─────────────────────────────────────────────┘
```

### 3.2 Data Flow (Unidirectional)

```
User Action → ViewModel → Use Case → Repository → Room/DataStore
                ↑                                       │
                └──── UI State (StateFlow) ◄── Flow ────┘
```

1. User interacts with a Composable.
2. Composable calls a ViewModel function.
3. ViewModel delegates to a Use Case (in `viewModelScope`).
4. Use Case calls Repository interface method.
5. Repository implementation reads/writes Room or DataStore.
6. Room returns `Flow<T>` which propagates up through Use Case → ViewModel → `StateFlow` → Composable recomposes.

### 3.3 State Management

Each screen ViewModel exposes a single `StateFlow<ScreenUiState>` using a `data class` for the UI state. User actions are modeled as function calls on the ViewModel (no event sealed class needed for simple screens — KISS).

```kotlin
// Example pattern
@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val getHistory: GetBreastfeedingHistoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreastfeedingUiState())
    val uiState: StateFlow<BreastfeedingUiState> = _uiState.asStateFlow()

    fun onStartSession(side: BreastSide) { /* ... */ }
}
```

---

## 4. Package Structure

```
com.babytracker/
│
├── BabyTrackerApp.kt                  # @HiltAndroidApp Application class
├── MainActivity.kt                    # Single Activity, setContent { }
│
├── navigation/
│   └── AppNavGraph.kt                 # NavHost with route definitions
│
├── di/
│   ├── DatabaseModule.kt              # @Module: Room DB, DAOs
│   └── DataStoreModule.kt             # @Module: DataStore instance
│
├── domain/
│   ├── model/
│   │   ├── Baby.kt                    # data class Baby(name, birthDate, allergies)
│   │   ├── BreastfeedingSession.kt    # data class with start, end, side, duration
│   │   ├── SleepRecord.kt             # data class with start, end, type (nap/night)
│   │   ├── SleepSchedule.kt           # data class for generated schedule
│   │   ├── BreastSide.kt              # enum: LEFT, RIGHT
│   │   ├── SleepType.kt               # enum: NAP, NIGHT_SLEEP
│   │   └── AllergyType.kt             # enum: CMPA, OTHER (extensible)
│   │
│   ├── repository/
│   │   ├── BabyRepository.kt          # interface
│   │   ├── BreastfeedingRepository.kt # interface
│   │   ├── SleepRepository.kt         # interface
│   │   └── SettingsRepository.kt      # interface
│   │
│   └── usecase/
│       ├── breastfeeding/
│       │   ├── StartBreastfeedingSessionUseCase.kt
│       │   ├── StopBreastfeedingSessionUseCase.kt
│       │   └── GetBreastfeedingHistoryUseCase.kt
│       │
│       ├── sleep/
│       │   ├── StartSleepRecordUseCase.kt
│       │   ├── StopSleepRecordUseCase.kt
│       │   ├── GetSleepHistoryUseCase.kt
│       │   └── GenerateSleepScheduleUseCase.kt
│       │
│       └── baby/
│           ├── SaveBabyProfileUseCase.kt
│           └── GetBabyProfileUseCase.kt
│
├── data/
│   ├── local/
│   │   ├── BabyTrackerDatabase.kt     # @Database, Room DB class
│   │   ├── dao/
│   │   │   ├── BreastfeedingDao.kt
│   │   │   └── SleepDao.kt
│   │   ├── entity/
│   │   │   ├── BreastfeedingEntity.kt
│   │   │   └── SleepEntity.kt
│   │   └── converter/
│   │       └── Converters.kt          # Room TypeConverters (Instant, enums)
│   │
│   └── repository/
│       ├── BabyRepositoryImpl.kt      # DataStore-backed
│       ├── BreastfeedingRepositoryImpl.kt  # Room-backed
│       ├── SleepRepositoryImpl.kt     # Room-backed
│       └── SettingsRepositoryImpl.kt  # DataStore-backed
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                   # MD3 dynamic color / custom theme
│   │   ├── Color.kt
│   │   └── Type.kt
│   │
│   ├── component/
│   │   ├── TimerDisplay.kt            # Reusable countdown/countup timer
│   │   ├── HistoryCard.kt             # Reusable card for history lists
│   │   └── SideSelector.kt            # Left/Right breast toggle
│   │
│   ├── onboarding/
│   │   ├── OnboardingScreen.kt
│   │   └── OnboardingViewModel.kt
│   │
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   │
│   ├── breastfeeding/
│   │   ├── BreastfeedingScreen.kt
│   │   ├── BreastfeedingHistoryScreen.kt
│   │   └── BreastfeedingViewModel.kt
│   │
│   ├── sleep/
│   │   ├── SleepTrackingScreen.kt
│   │   ├── SleepHistoryScreen.kt
│   │   ├── SleepScheduleScreen.kt
│   │   └── SleepViewModel.kt
│   │
│   └── settings/
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
│
└── util/
    ├── DateTimeExt.kt                 # Extension functions for Instant/Duration formatting
    └── FlowExt.kt                     # Utility extensions if needed
```

### 4.1 Key Decisions

- **Flat feature packages under `ui/`** — each feature has its Screen + ViewModel co-located. No sub-packaging beyond this.
- **Domain models are separate from Room entities** — entities have Room annotations; domain models are clean. Conversion is done via extension functions on the entity (`fun BreastfeedingEntity.toDomain(): BreastfeedingSession`), not via Mapper classes.
- **One ViewModel per feature screen** — a single `SleepViewModel` can serve tracking, history, and schedule if they share state. Split only if complexity warrants it.
- **Use Cases are classes, not interfaces** — each has a single `operator fun invoke()`. No interface abstraction on use cases unless there is a real second implementation.

---

## 5. Database Schema

### 5.1 Room Database

**Database name:** `baby_tracker_db`
**Version:** 1

#### Table: `breastfeeding_sessions`

| Column | Type | Constraints |
|---|---|---|
| `id` | `Long` | PRIMARY KEY, autoGenerate |
| `start_time` | `Long` | NOT NULL (epoch millis) |
| `end_time` | `Long` | NULLABLE (null = in progress) |
| `starting_side` | `String` | NOT NULL (LEFT / RIGHT) |
| `switch_time` | `Long` | NULLABLE (epoch millis when switched sides) |
| `notes` | `String` | NULLABLE |

#### Table: `sleep_records`

| Column | Type | Constraints |
|---|---|---|
| `id` | `Long` | PRIMARY KEY, autoGenerate |
| `start_time` | `Long` | NOT NULL (epoch millis) |
| `end_time` | `Long` | NULLABLE (null = in progress) |
| `sleep_type` | `String` | NOT NULL (NAP / NIGHT_SLEEP) |
| `notes` | `String` | NULLABLE |

### 5.2 DataStore Keys (Preferences)

| Key | Type | Purpose |
|---|---|---|
| `baby_name` | `String` | Baby's name |
| `baby_birth_date` | `Long` | Birth date as epoch millis |
| `allergies` | `String` | Comma-separated allergy codes |
| `max_per_breast_minutes` | `Int` | Optional max time per breast (0 = disabled) |
| `max_total_feed_minutes` | `Int` | Optional max total feed time (0 = disabled) |
| `onboarding_complete` | `Boolean` | Whether initial setup is done |

---

## 6. Navigation

### 6.1 Route Structure

```
Onboarding (conditional start)
  └── name, birth date, allergies

Home (default start after onboarding)
  ├── Breastfeeding
  │   ├── Active Session (timer)
  │   └── History
  ├── Sleep
  │   ├── Active Tracking
  │   ├── History
  │   └── Schedule
  └── Settings
```

### 6.2 Navigation Implementation

- Single `NavHost` in `MainActivity`.
- Start destination determined by `onboarding_complete` flag from DataStore.
- Bottom navigation bar on Home with three tabs: Feeding, Sleep, Settings.
- Nested navigation graphs per feature are unnecessary at this scale — flat routes with string constants are sufficient.

```kotlin
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val BREASTFEEDING = "breastfeeding"
    const val BREASTFEEDING_HISTORY = "breastfeeding/history"
    const val SLEEP_TRACKING = "sleep"
    const val SLEEP_HISTORY = "sleep/history"
    const val SLEEP_SCHEDULE = "sleep/schedule"
    const val SETTINGS = "settings"
}
```

---

## 7. Dependency Injection

### 7.1 Hilt Modules

**`DatabaseModule`** (`@InstallIn(SingletonComponent::class)`)
- Provides `BabyTrackerDatabase` as `@Singleton`.
- Provides each DAO from the database instance.

**`DataStoreModule`** (`@InstallIn(SingletonComponent::class)`)
- Provides `DataStore<Preferences>` as `@Singleton`.

**Repository bindings** (`@InstallIn(SingletonComponent::class)`)
- Binds `BreastfeedingRepositoryImpl` → `BreastfeedingRepository`.
- Binds `SleepRepositoryImpl` → `SleepRepository`.
- Binds `BabyRepositoryImpl` → `BabyRepository`.
- Binds `SettingsRepositoryImpl` → `SettingsRepository`.

**Use Cases** — constructor-injected directly, no module needed. Hilt resolves them automatically.

### 7.2 Scope Decisions

| Component | Scope | Reason |
|---|---|---|
| Database, DataStore | `@Singleton` | Single instance for app lifetime |
| Repositories | `@Singleton` | Stateless, safe to share |
| Use Cases | Unscoped | Lightweight, created per injection site |
| ViewModels | `@HiltViewModel` | Scoped to navigation back stack entry |

---

## 8. Testing Strategy

### 8.1 Test Pyramid

```
        ┌─────────┐
        │ UI Tests│  Compose UI tests (critical flows only)
        ├─────────┤
      ┌─┤Integr.  │  Repository + Room in-memory DB
      │ ├─────────┤
      │ │  Unit   │  Use Cases, ViewModels, domain logic
      └─┴─────────┘
```

### 8.2 Unit Tests

| Target | Framework | What to test |
|---|---|---|
| Use Cases | JUnit 5 + MockK | Business logic, edge cases, input validation |
| ViewModels | JUnit 5 + MockK + Turbine | State transitions, Flow emissions, error states |
| Domain Models | JUnit 5 | Computed properties, validation rules |
| Sleep Algorithm | JUnit 5 | Wake window calculations, nap transition logic, schedule generation |

### 8.3 Integration Tests

| Target | Framework | What to test |
|---|---|---|
| DAOs | JUnit 5 + Room in-memory DB | Query correctness, insert/update/delete, Flow emissions |
| Repositories | JUnit 5 + Room in-memory DB | End-to-end data flow from repository interface to DB |
| DataStore repos | JUnit 5 + TestDataStore | Preference read/write cycles |

### 8.4 UI Tests

| Target | Framework | What to test |
|---|---|---|
| Onboarding flow | Compose UI Test | Complete onboarding saves profile correctly |
| Breastfeeding timer | Compose UI Test | Start/stop/switch sides produces correct session |
| Navigation | Compose UI Test | Bottom nav routing, conditional onboarding |

### 8.5 Test Conventions

- Test files mirror source: `com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCaseTest`.
- Naming: `fun methodName_condition_expectedResult()`.
- Each test class has a `@BeforeEach` setup block, not `@Before`.
- Use `Dispatchers.Unconfined` or `StandardTestDispatcher` in ViewModel tests via `@OptIn(ExperimentalCoroutinesApi::class)`.
- Use Turbine's `test { }` block for Flow assertion.

---

## 9. Build Configuration

### 9.1 Gradle (KTS)

- **Version Catalog** (`libs.versions.toml`) for all dependency versions.
- **compileSdk:** 35
- **minSdk:** 26 (Android 8.0 — covers 95%+ of active devices, provides `java.time` API without desugaring)
- **targetSdk:** 35
- **Kotlin JVM target:** 17

### 9.2 ProGuard / R8

Enabled for release builds. Room and Hilt rules are auto-included via their Gradle plugins.

---

## 10. Feature Specs Roadmap

Each feature will be developed and specified independently. The following specs will be created in sequence:

| Spec ID | Feature | Dependencies |
|---|---|---|
| SPEC-001 | App Structure (this document) | — |
| SPEC-002 | Onboarding (Baby Profile + Allergies) | SPEC-001 |
| SPEC-003 | Breastfeeding Tracking | SPEC-001, SPEC-002 |
| SPEC-004 | Sleep Tracking | SPEC-001, SPEC-002 |
| SPEC-005 | Sleep Schedule Generator | SPEC-001, SPEC-004, Sleep Research |

---

## 11. Acceptance Criteria for This Spec

- [ ] Project compiles with all dependencies resolved.
- [ ] Hilt injection graph builds without errors.
- [ ] Room database creates successfully with empty tables.
- [ ] DataStore reads/writes baby name round-trip.
- [ ] Navigation shows Onboarding on first launch, Home on subsequent launches.
- [ ] MD3 theme applied with light/dark mode support.
- [ ] Empty Home screen renders with bottom navigation (Feeding, Sleep, Settings tabs).
- [ ] All placeholder screens are reachable via navigation.
- [ ] Unit test for at least one use case runs green.
- [ ] Integration test for at least one DAO runs green with in-memory Room DB.
