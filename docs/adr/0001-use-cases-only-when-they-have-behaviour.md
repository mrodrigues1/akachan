# Use cases exist only when they have behaviour

Status: proposed (AKACHAN-301) — until accepted, SPEC-001 §3.2 remains the operative rule: new work keeps scaffolding the use-case tier. Everything below describes what changes **on acceptance**.

SPEC-001 mandates `ViewModel → UseCase → Repository` for every flow. In practice that produced ~40 one-line pass-through use cases (`GetSleepHistoryUseCase = repository.getAllRecords()`) whose tests only assert that a mock was called, and a plain CRUD concept spans ~8 files. Meanwhile 24 ViewModels already inject repository interfaces directly without tripping any konsist rule — the mandate was prose-only, never test-enforced.

**Decision:** the use-case tier is optional. A use case earns its file only when it contains behaviour — a branch, computation, time math, or orchestration across repositories (e.g. `GenerateSleepScheduleUseCase`, `PredictNextFeedUseCase`, the sharing op and export/backup clusters). For plain CRUD, ViewModels call the repository interface (`com.babytracker.domain.repository.*`) directly. Pass-through use cases and their tautological tests are deleted, not maintained.

The repository seam stays untouched: it is the konsist-enforced isolation point (`LayerIsolationTest` blocks ViewModels from importing `com.babytracker.data.*` and DAOs) and the entity↔domain swap point.

## Considered options

- **Keep the mandate** — rejected: the ~40 relay modules fail the deletion test (removing one just moves a call up a line) and their tests assert nothing.
- **Drop the repository tier instead** — rejected: repositories carry the entity↔domain mapping and are the only test-enforced boundary; deleting them would put Room types in ViewModels.

## Consequences

- SPEC-001 §3.2 data-flow steps 3–4 need amending to "ViewModel calls a use case when one exists, otherwise the repository interface".
- New-feature work no longer scaffolds `Get*/Observe*/Delete*UseCase` by default; write one only when logic appears.
- The remaining deep use cases become a meaningful signal ("this concept has domain logic") instead of noise.
