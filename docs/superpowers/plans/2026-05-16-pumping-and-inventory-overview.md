# Pumping Sessions & Milk Inventory — Implementation Plan Overview

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pumping session tracking and a milk-bag inventory to Akachan, mirroring breastfeeding/sleep feature patterns. Inventory total syncs to the partner; pumping sessions and bag rows remain local.

**Architecture:** Three-layer clean architecture inside the existing single `:app` module. Two new top-level feature folders (`*/pumping/`, `*/inventory/`). Room v2 → v3 with `MIGRATION_2_3` adding `pumping_sessions` and `milk_bags`. Sharing reuses the existing channel — `ShareSnapshot` gains three inventory fields, synced via `SyncToFirestoreUseCase` on every inventory mutation.

**Tech Stack:** Kotlin 2.3.20, Jetpack Compose (Material 3), Hilt, Room 2.8.4, Coroutines + Flow, Firebase Firestore (existing), JUnit 5 + MockK + Turbine + Compose UI Test.

**Source spec:** [`docs/superpowers/specs/2026-05-16-pumping-and-inventory-design.md`](../specs/2026-05-16-pumping-and-inventory-design.md)

**Linear project:** [Pumping Sessions & Milk Inventory](https://linear.app/akachan/project/pumping-sessions-and-milk-inventory-9c8b359b3e75/overview)

---

## Workflow convention

For every task: **implement first, then write tests, then commit.** No TDD. Follow `CLAUDE.md` conventional-commits style. Before each commit run:

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test
```

Instrumentation tests run separately (`./gradlew connectedAndroidTest`) when the task touches DAOs, migrations, or Compose UI.

If `detekt` flags anything, fix the code — never `@Suppress`. Each detekt remediation is its own commit (`fix(detekt): ...`).

---

## Task summary

| # | Task | Plan file | Depends on |
|---|------|-----------|-----------|
| 1 | Domain models | [task-01-domain-models.md](2026-05-16-pumping-and-inventory/task-01-domain-models.md) | — |
| 2 | Room data layer (entities, DAOs, MIGRATION_2_3, DI) | [task-02-data-layer.md](2026-05-16-pumping-and-inventory/task-02-data-layer.md) | 1 |
| 3 | Repository layer (interfaces, impls, RepositoryModule) | [task-03-repositories.md](2026-05-16-pumping-and-inventory/task-03-repositories.md) | 1, 2 |
| 4 | Pumping use cases | [task-04-pumping-usecases.md](2026-05-16-pumping-and-inventory/task-04-pumping-usecases.md) | 3 |
| 5 | Inventory use cases + sharing fields + sync trigger | [task-05-inventory-and-sync.md](2026-05-16-pumping-and-inventory/task-05-inventory-and-sync.md) | 3 |
| 6 | Pumping screen + VM + AddBagPromptSheet + nav route | [task-06-pumping-screen.md](2026-05-16-pumping-and-inventory/task-06-pumping-screen.md) | 4, 5 |
| 7 | Pumping history + EditPumpingSessionSheet + nav | [task-07-pumping-history.md](2026-05-16-pumping-and-inventory/task-07-pumping-history.md) | 4, 6 |
| 8 | Inventory screen + AddBagSheet + nav route | [task-08-inventory-screen.md](2026-05-16-pumping-and-inventory/task-08-inventory-screen.md) | 5 |
| 9 | Home screen 2×2 grid integration | [task-09-home-integration.md](2026-05-16-pumping-and-inventory/task-09-home-integration.md) | 6, 8 |
| 10 | Partner dashboard inventory card | [task-10-partner-dashboard.md](2026-05-16-pumping-and-inventory/task-10-partner-dashboard.md) | 5 |
| 11 | Final quality gates + manual smoke | [task-11-final-qa.md](2026-05-16-pumping-and-inventory/task-11-final-qa.md) | 1–10 |

A linear `1 → 11` order is safe. Tasks 4 and 5 can run in parallel after task 3 lands. Tasks 6/7/8/10 can run in parallel after their dependencies land.

---

## Decisions locked in from the spec (single source of truth)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Logging UX | Live timer **and** manual entry |
| 2 | Session → bag link | Auto-prompt on session stop (`AddBagPromptSheet`) |
| 3 | Units | mL only |
| 4 | Storage location | Skip — single pool |
| 5 | Home placement | Card on summary grid; no bottom-bar button |
| 6 | Sharing | Inventory **total** only |
| 7 | Bag removal | Soft delete via `usedAt`; hard delete via overflow menu |
| 8 | Edit/delete sessions | Mirrors `EditBreastfeedingSessionSheet` |
| 9 | Notifications | Skip for v1 |
| 10 | Session shape | Single `breast: LEFT/RIGHT/BOTH` enum (no side-switch model) |
| 11 | Screen split | 3 routes (`PumpingScreen`, `PumpingHistoryScreen`, `InventoryScreen`) |

---

## What NOT to touch

- No new Hilt modules. Extend existing `DatabaseModule`, `RepositoryModule`, `SharingModule`.
- No new Firestore sub-collections. Only three top-level fields on `shares/{shareCode}`.
- No notifications/alarms for pumping or inventory in v1.
- No multi-module changes. Everything stays in `:app`.
- Pumping sessions and `milk_bags` rows must never be written to Firestore. Only the three inventory summary fields.
- Do not introduce `Mapper` classes or sealed `Result<>` wrappers (see `CLAUDE.md`).

---

## Cross-cutting conventions

- Domain models: pure Kotlin data classes in `com.babytracker.domain.model` — zero framework imports.
- Entity ↔ domain: extension functions on entity/domain types (e.g. `PumpingEntity.toDomain()`).
- Use cases: `suspend operator fun invoke(...)` — no interfaces unless multiple implementations exist.
- ViewModels: expose a single `StateFlow<*UiState>` + handler functions; collect repository flows inside `viewModelScope`.
- Date/time: `java.time.Instant`; epoch millis as `Long` in Room and DataStore.
- Money/volume: `Int` mL only (positive integers).
- Theme tokens: see `CLAUDE.md` — Pumping card uses `tertiaryContainer` (green); Inventory card uses `surfaceVariant`.

---

## Codex adversarial review fixes (2026-05-16)

Three findings folded into the plan after initial draft:

1. **Firestore field path** (task 5) — `syncInventory` writes under the nested `data` map (`data.inventoryTotalMl`, `data.inventoryBagCount`, `data.inventoryUpdatedAt`) and updates `snapshotToMap` + `mapToSnapshot`. Original draft wrote at the document root, where partner devices never read.
2. **Initial share-code snapshot** (task 5) — `GenerateShareCodeUseCase` injects `InventoryRepository` and seeds the initial `ShareSnapshot` with inventory fields. Original draft only touched `SyncToFirestoreUseCase.syncFull`, leaving the first snapshot blank for primary users with pre-existing bags.
3. **Migration index order** (task 2) — `MIGRATION_2_3` creates the start-time index with `DESC` to match `Index.Order.DESC` declared on `PumpingEntity`. Original draft missed the order, which would fail Room's schema validation.

## Acceptance criteria for the whole feature

- `./gradlew ktlintFormat && ./gradlew detekt && ./gradlew test` clean.
- `./gradlew connectedAndroidTest` passes (covers `PumpingDaoTest`, `MilkBagDaoTest`, `MigrationTest` v2→v3, and the new Compose screen tests).
- A primary user can: start the timer, stop it, accept or skip the bag prompt, see the bag in inventory, mark it used, delete it.
- A primary user can switch to **Manual entry** mode and save a completed session w/ volume.
- A primary user can edit and delete a session from `PumpingHistoryScreen`.
- A partner user sees the inventory total update within ~1 round trip after any Add / MarkUsed / Delete on the primary device.
- DB upgrade from a v2 install installs cleanly on top of an existing user's DB (verified via `MigrationTest`).
- Home screen shows a 2×2 summary grid (Feeding/Sleep row 1, Pumping/Inventory row 2). Bottom action bar still shows Feeding/Sleep only.
