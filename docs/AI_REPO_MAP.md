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
- `app/src/main/java/com/babytracker/tile`: Quick Settings tiles for feed and sleep (start/stop from the shade).
- `app/src/main/java/com/babytracker/widget`: Glance home-screen widgets (baby status, milk stash) and their refresh/sync plumbing.
- `app/src/main/java/com/babytracker/sharing`: Partner sharing domain, Firebase service, repository, and use cases.
- `app/src/main/java/com/babytracker/export`: Backup export/import and PDF report generation.
- `app/src/main/java/com/babytracker/util`: Date/time, Flow, notification, and update-checking helpers.
- `app/src/main/java/com/babytracker/debug`: Debug-build data seeding.

## Tests and config
- `app/src/test`: Unit tests with JUnit 5, MockK, Turbine, and coroutine test utilities.
- `app/src/androidTest`: Instrumentation, Room in-memory DB, and Compose UI tests.
- `gradle/libs.versions.toml`: Authoritative dependency versions.
- `config/detekt.yml`: Detekt rules tuned for this app.
- `docs/superpowers/specs/`: feature specs since ~May 2026 (dated filenames — search by feature keyword). Read the relevant spec before feature work.
- `docs/adr/`: architectural decision records — the retired early core specs (`specs/SPEC-00x`, in git history) were distilled into ADRs 0001–0005.

## Common tasks
- Find a feature's files: `docs/AI_FEATURE_MAP.md` (screens, ViewModels, repositories, entities, routes per feature).
- Add screen: check `app/src/main/java/com/babytracker/navigation`, the relevant `ui/<feature>` package, and the established ViewModel plus `*UiState` pattern.
- Add DB field: update Entity, DAO queries if needed, Room migration, repository conversion functions, and tests — then follow `docs/AI_DATA_CHANGE_CHECKLIST.md` for the backup/CSV/partner-sync ripple.
- Add use case: put a single-responsibility class under `domain/usecase/<feature>` and inject the repository interface.
- Add repository behavior: update the domain repository interface, implementation in `data/repository`, DI binding if a new implementation is introduced, and tests.
- Add notification behavior: check `manager/`, `receiver/`, `util/NotificationHelper.kt`, manifest receivers, and scheduler DI bindings.
- Add partner sharing behavior: keep Firebase-specific work inside `sharing/` and `di/SharingModule.kt`.
- Change Home tiles or their reorder behavior: the `HomeTile` enum (`domain/model/HomeTile.kt`) is the canonical tile set, `DEFAULT_ORDER`, and serialize/`reconcile` logic; tile rendering lives in `ui/home/HomeTileContent.kt` and `ui/home/HomeScreen.kt`; drag/accessibility reorder state in `ui/home/HomeViewModel.kt`; the order persists via `getHomeTileOrder`/`setHomeTileOrder`/`clearHomeTileOrder` on `SettingsRepository` (DataStore-backed, comma-joined tile names).
- Update dependencies: edit `gradle/libs.versions.toml` — it is authoritative; AGENTS.md deliberately lists no versions. Keep the DataStore 1.1.1 and Kover 0.9.1 pins (newer versions break tests/coverage).

## Do not inspect unless needed
- `build/`
- `.gradle/`
- `generated/`
- `.idea/`
- `*.iml`
- snapshots
- screenshots
- lockfiles unless dependency changes are required
