# Single-module MVVM with an enforced repository seam

Status: accepted (decided 2026-04-03 in SPEC-001, now retired into this ADR — original in git history; use-case tier amended by ADR-0001)

The app is a single `:app` module with three layers — UI (Compose screens → ViewModels → `*UiState`), domain (pure-Kotlin models, repository interfaces, behaviour-only use cases per ADR-0001), data (repository implementations → Room/DataStore) — with unidirectional data flow (`Flow` up, function calls down). The repository interface (`domain/repository/*`) is the enforced isolation seam: konsist's `LayerIsolationTest` blocks ViewModels from importing `com.babytracker.data.*` or DAOs, and it is the entity↔domain swap point — conversion via extension functions (`Entity.toDomain()` / `Domain.toEntity()`), never Mapper classes.

Room holds relational tracking data (sessions, records, events); DataStore holds flat key-value preferences. Both were chosen for native `Flow` support.

## Deliberate anti-goals

Recorded so nobody "fixes" them later:

- No multi-module Gradle setup — the app's size doesn't warrant the build complexity.
- No Mapper classes, no `BaseViewModel`/`BaseFragment`, no `sealed class Result<T>` wrappers.
- No pagination libraries — data volumes top out at thousands of rows.
- No network layer beyond the partner-sharing feature (ADR-0002).
