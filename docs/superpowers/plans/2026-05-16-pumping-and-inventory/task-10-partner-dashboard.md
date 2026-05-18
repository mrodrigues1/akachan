# Task 10 — Partner dashboard inventory card

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Show the inventory summary to the partner user. Read-only.

**Depends on:** Task 5 (ShareSnapshot has the three new fields).

## Files

- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardViewModel.kt` *(if necessary — only if the snapshot doesn't flow through already)*
- Test (instrumentation): `app/src/androidTest/java/com/babytracker/ui/partner/PartnerDashboardScreenTest.kt` *(extend or create)*

## Implementation

### Step 1: Read what `PartnerDashboardViewModel` exposes

Open the existing file and confirm `ShareSnapshot` (or a UI mirror of it) is exposed. The three new fields (`inventoryTotalMl`, `inventoryBagCount`, `inventoryUpdatedAt`) ride along automatically since `ShareSnapshot` is a data class. No VM changes needed unless the partner UI uses a separate UI-state mirror — in that case add the same three nullable fields to the UI state.

### Step 2: Add the inventory card to `PartnerDashboardScreen`

Place a new card after the baby + last-feeding cards. Render conditionally on `snapshot.inventoryBagCount != null && snapshot.inventoryBagCount > 0`. When `bagCount == 0` or null, hide the card (parents without an inventory should not see clutter).

```kotlin
@Composable
private fun PartnerInventoryCard(
    totalMl: Int,
    bagCount: Int,
    updatedAtMs: Long?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Milk stash: $totalMl millilitres in $bagCount bags."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🧊 Milk stash",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$totalMl mL · $bagCount bags",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (updatedAtMs != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Updated ${Duration.between(Instant.ofEpochMilli(updatedAtMs), Instant.now()).formatElapsedAgo()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

Then in the screen body, after the existing cards:

```kotlin
val inventoryTotalMl = snapshot.inventoryTotalMl
val inventoryBagCount = snapshot.inventoryBagCount
if (inventoryTotalMl != null && inventoryBagCount != null && inventoryBagCount > 0) {
    PartnerInventoryCard(
        totalMl = inventoryTotalMl,
        bagCount = inventoryBagCount,
        updatedAtMs = snapshot.inventoryUpdatedAt,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

Match the existing card spacing pattern (likely `Spacer(Modifier.height(12.dp))` above the new card).

## Tests

### `PartnerDashboardScreenTest`

- When `inventoryBagCount = 0` and `inventoryTotalMl = 0`: the inventory card is **not** rendered (assert `onNodeWithText("Milk stash")` does not exist).
- When `inventoryBagCount = 3` and `inventoryTotalMl = 240`: text "240 mL · 3 bags" is rendered.
- When `inventoryUpdatedAt != null`: an "Updated …" line appears under the totals.

Render via the existing `PartnerDashboardScreen` test harness (fake VM or direct content composable, whichever pattern the file already uses). Look at the current file for the exact approach.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
com.babytracker.ui.partner.PartnerDashboardScreenTest
```

Expected: all green.

## Commit

```
feat(partner): render inventory summary card on partner dashboard

Reads ShareSnapshot.inventoryTotalMl, inventoryBagCount, and
inventoryUpdatedAt. Card hides when no bags are stored to keep the
dashboard tidy for families without a stash.
```
