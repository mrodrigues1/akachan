# Repo map

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

## Android app
- `app/src/main/java/com/babytracker/ui`: Compose screens, reusable components, theme, and feature ViewModels.
- `app/src/main/java/com/babytracker/data`: Room database, entities, DAOs, converters, and repository implementations.
- `app/src/main/java/com/babytracker/domain`: Pure Kotlin models, repository interfaces, and business use cases.
- `app/src/main/java/com/babytracker/navigation`: App routes and Compose navigation graph.
- `app/src/main/java/com/babytracker/di`: Hilt modules for database, DataStore, repositories, notifications, and sharing.
- `app/src/main/java/com/babytracker/manager`: Notification scheduler interfaces and implementations.
- `app/src/main/java/com/babytracker/receiver`: Broadcast receivers for notification actions and alarm triggers.
- `app/src/main/java/com/babytracker/sharing`: Partner sharing domain, Firebase service, repository, and use cases.
- `app/src/main/java/com/babytracker/util`: Date/time, Flow, notification, and update-checking helpers.

## Tests and config
- `app/src/test`: Unit tests with JUnit 5, MockK, Turbine, and coroutine test utilities.
- `app/src/androidTest`: Instrumentation, Room in-memory DB, and Compose UI tests.
- `gradle/libs.versions.toml`: Authoritative dependency versions.
- `config/detekt.yml`: Detekt rules tuned for this app.
- `specs/`: Product and architecture specs. Read relevant specs before feature work.

## Common tasks
- Add screen: check `app/src/main/java/com/babytracker/navigation`, the relevant `ui/<feature>` package, and the established ViewModel plus `*UiState` pattern.
- Add DB field: update Entity, DAO queries if needed, Room migration, repository conversion functions, and tests.
- Add use case: put a single-responsibility class under `domain/usecase/<feature>` and inject the repository interface.
- Add repository behavior: update the domain repository interface, implementation in `data/repository`, DI binding if a new implementation is introduced, and tests.
- Add notification behavior: check `manager/`, `receiver/`, `util/NotificationHelper.kt`, manifest receivers, and scheduler DI bindings.
- Add partner sharing behavior: keep Firebase-specific work inside `sharing/` and `di/SharingModule.kt`.
- Change Home tiles or their reorder behavior: the `HomeTile` enum (`domain/model/HomeTile.kt`) is the canonical tile set, `DEFAULT_ORDER`, and serialize/`reconcile` logic; tile rendering lives in `ui/home/HomeTileContent.kt` and `ui/home/HomeScreen.kt`; drag/accessibility reorder state in `ui/home/HomeViewModel.kt`; the order persists via `getHomeTileOrder`/`setHomeTileOrder`/`clearHomeTileOrder` on `SettingsRepository` (DataStore-backed, comma-joined tile names).
- Update dependencies: edit `gradle/libs.versions.toml`, then update `CLAUDE.md` and `AGENTS.md` tech stack tables if versions changed.

## Do not inspect unless needed
- `build/`
- `.gradle/`
- `generated/`
- `.idea/`
- `*.iml`
- snapshots
- screenshots
- lockfiles unless dependency changes are required
