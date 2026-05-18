# Task 11 — Final quality gates + manual smoke

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md).

**Goal:** Verify the feature is production-ready before merging. No code changes unless something breaks.

**Depends on:** All previous tasks (1–10).

## Acceptance checks

- [ ] **ktlint clean.** Run `./gradlew ktlintCheck`. If anything fails, run `./gradlew ktlintFormat` and commit (`style: ktlintFormat for pumping feature`).
- [ ] **detekt clean.** Run `./gradlew detekt`. Fix violations in code — never `@Suppress`. Each fix in its own commit (`fix(detekt): ...`).
- [ ] **Unit tests pass.** Run `./gradlew test`. Pay special attention to:
  - `com.babytracker.domain.model.*`
  - `com.babytracker.data.repository.PumpingRepositoryImplTest`
  - `com.babytracker.data.repository.InventoryRepositoryImplTest`
  - `com.babytracker.domain.usecase.pumping.*`
  - `com.babytracker.domain.usecase.inventory.*`
  - `com.babytracker.sharing.usecase.SyncToFirestoreUseCaseTest`
  - `com.babytracker.ui.pumping.PumpingViewModelTest`
  - `com.babytracker.ui.pumping.PumpingHistoryViewModelTest`
  - `com.babytracker.ui.inventory.InventoryViewModelTest`
  - `com.babytracker.ui.home.HomeViewModelTest`
- [ ] **Instrumentation tests pass.** Run `./gradlew connectedAndroidTest`. Coverage includes:
  - `PumpingDaoTest`, `MilkBagDaoTest`
  - `MigrationTest` (`migrate2To3_*` case)
  - `PumpingScreenTest`, `AddBagPromptSheetTest`, `PumpingHistoryScreenTest`, `EditPumpingSessionSheetTest`
  - `InventoryScreenTest`, `AddBagSheetTest`
  - `HomeScreenTest` (Pumping + Inventory cards)
  - `PartnerDashboardScreenTest` (inventory card hide/show)
- [ ] **Manual smoke on a real device or emulator.** Walk the golden path twice — once as primary user, once as partner.

  Primary (single device):
  1. Install over a v2 build (fresh install is also acceptable; both verify migration via Room).
  2. Open Home — both new cards visible.
  3. Tap **Pumping** → switch to Manual mode → fill start/end/breast/volume → Save → confirm bag prompt → tap **Add to stash**.
  4. Open **Inventory** from Home — summary shows the new bag.
  5. Add a second bag manually (FAB).
  6. Mark the oldest used.
  7. Delete a bag via overflow menu.
  8. Go back to **Pumping** → start a timer → pause → resume → stop → skip bag prompt.
  9. Go to **Pumping** history (top-right icon) → tap a row → edit volume → save → delete a row.

  Partner (second device):
  1. Enter the share code → land on partner dashboard.
  2. Confirm the inventory card shows the current totals after each primary action above (allow up to ~5 seconds for Firestore propagation).
  3. Confirm the inventory card disappears once the primary device deletes the last bag.

- [ ] **No regressions** in breastfeeding and sleep flows. Smoke each: start/stop a feeding, start/stop a sleep record, view history. The Firestore sync for sessions/sleep should still fire as it did before this feature.

## If something fails

- DB migration error on real device: re-run with a clean install, inspect `MigrationTest` for any schema mismatch, and align `MIGRATION_2_3` with the message.
- Sync field mismatch on partner device: check `FirestoreSharingService.syncInventory` writes exactly `inventoryTotalMl`, `inventoryBagCount`, `inventoryUpdatedAt` — case and spelling must match the data class fields.
- Compose preview failures: run `./gradlew assembleDebug` and resolve the first error before re-running tests.

## Commit (only if remediation was needed)

Use the relevant scope (`fix(pumping): ...`, `fix(inventory): ...`, `fix(detekt): ...`).

## Wrap up

When all checks pass:

- Push the branch.
- Open the PR (target `main`). The PR description should link the spec, this overview, and call out the DB version bump (v2 → v3).
- Verify CI is green before requesting review.
