# Diaper Export / Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-164

**Goal:** Include diaper changes in the JSON backup (format v4, with restore), the CSV export, and the PDF report.

**Architecture:** Adds `DiaperBackup` to the versioned `BackupData` (bumped 3 → 4; the new list defaults to empty so older importers ignore it). `BackupSourceImpl` reads `diaperDao().getAllOnce()` inside the existing transaction; `BackupImporterImpl.mergeDiapers` inserts with identity-based dedup `[timestamp, type, notes, createdAt]`. `ExportCsvUseCase` gains a `diapers` section. The PDF report gains a diaper section mirroring the existing sleep section, including the fragile `countPages` pagination simulation that must stay in sync with `render`.

**Tech Stack:** Kotlinx Serialization JSON, Room, Android PdfDocument, JUnit 5 + MockK, Robolectric (importer/DAO).

**Dependencies:** Plan 1 only (`DiaperEntity`, `DiaperDao.getAllOnce()`, `DiaperChange`, `DiaperRepository.getBetween`, `diaperDao()` on the database). Independent of plans 2–5.

**Suggested implementation branch:** `feat/diaper-export-import`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `DiaperBackup` + backup format v4

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/model/BackupData.kt`
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ExportBackupUseCase.kt`

- [ ] **Step 1:** In `BackupData.kt`, bump the version and add the field + type:

```kotlin
const val CURRENT_BACKUP_FORMAT_VERSION = 4
```

```kotlin
// inside BackupData, after milestones:
// Added in format version 4; default-empty so pre-v4 backups still deserialize.
val diapers: List<DiaperBackup> = emptyList(),
```

```kotlin
@Serializable
data class DiaperBackup(
    val id: Long,
    val timestamp: Long,
    val type: String,
    val notes: String?,
    val createdAt: Long,
)
```

> Bumping `CURRENT_BACKUP_FORMAT_VERSION` to 4 is what makes v4 backups valid: `ValidateBackupUseCase`/`ImportBackupUseCase` gate on this constant (reject only versions newer than current). Confirm no `when (backupFormatVersion)` branch needs a v4 case — the new `diapers` field is purely additive, so none should.

- [ ] **Step 2:** In `ExportBackupUseCase.kt`, pass diapers into the assembled `BackupData(...)`:

```kotlin
diapers = tracking.diapers,
```

- [ ] **Step 3: Commit** `feat(export): add DiaperBackup and bump backup format to v4`

---

### Task 2: Read diapers into the backup snapshot

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/BackupSource.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupSourceImpl.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt`
- Test: `app/src/test/java/com/babytracker/export/data/DiaperBackupConvertersTest.kt`

- [ ] **Step 1:** In `BackupSource.kt`, add to `TrackingSnapshot` (default-empty for source consistency):

```kotlin
val diapers: List<DiaperBackup> = emptyList(),
```

(add `import com.babytracker.export.domain.model.DiaperBackup`)

- [ ] **Step 2:** In `BackupSourceImpl.readTracking()`, add the read inside the existing `db.withTransaction { TrackingSnapshot(...) }`:

```kotlin
diapers = db.diaperDao().getAllOnce().map { it.toBackup() },
```

- [ ] **Step 3:** In `BackupConverters.kt`, add the converters (add imports for `DiaperEntity` and `DiaperBackup`):

```kotlin
fun DiaperEntity.toBackup() = DiaperBackup(
    id = id, timestamp = timestamp, type = type, notes = notes, createdAt = createdAt,
)

fun DiaperBackup.toEntity() = DiaperEntity(
    id = id, timestamp = timestamp, type = type, notes = notes, createdAt = createdAt,
)
```

- [ ] **Step 4: Test** the converters:

```kotlin
package com.babytracker.export.data

import com.babytracker.data.local.entity.DiaperEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiaperBackupConvertersTest {
    @Test
    fun `entity to backup to entity round trips`() {
        val entity = DiaperEntity(id = 3, timestamp = 100, type = "BOTH", notes = "n", createdAt = 50)
        assertEquals(entity, entity.toBackup().toEntity())
    }
}
```

- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.export.data.DiaperBackupConvertersTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(export): read diapers into the backup snapshot`

---

### Task 3: Restore diapers on import

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/BackupImporter.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt`

- [ ] **Step 1:** In `BackupImporter.kt`, add a defaulted field to `ImportCounts`:

```kotlin
val diapersInserted: Int = 0,
```

- [ ] **Step 2:** In `BackupImporterImpl.kt`:
  - Add the import `import com.babytracker.data.local.entity.DiaperEntity`.
  - In `merge(...)`, call the new merge and include the count:

```kotlin
val diapers = mergeDiapers(data)
ImportCounts(bf, sleep, pumpInserted, bags, bottles, growth, milestones, diapers)
```

  - Add the merge function (mirrors `mergeGrowth`; identity = all fields except autogen id):

```kotlin
private suspend fun mergeDiapers(data: BackupData): Int {
    val seen = db.diaperDao().getAllOnce().map { it.identity() }.toMutableSet()
    var inserted = 0
    for (d in data.diapers) {
        val entity = DiaperEntity(
            timestamp = d.timestamp, type = d.type, notes = d.notes, createdAt = d.createdAt,
        )
        if (seen.add(entity.identity())) {
            db.diaperDao().insert(entity)
            inserted++
        }
    }
    return inserted
}
```

  - Add the identity helper alongside the others:

```kotlin
private fun DiaperEntity.identity() = listOf(timestamp, type, notes, createdAt)
```

- [ ] **Step 3: Test** — extend `app/src/test/java/com/babytracker/export/data/BackupImporterImplTest.kt` (Robolectric + in-memory DB): import a backup with two diapers → both inserted; re-import the same backup → `diapersInserted == 0` (dedup). Follow the existing milestone/growth import test cases in that file.
- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.export.data.BackupImporterImplTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(export): restore diapers from backup with dedup`

---

### Task 4: CSV export section

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ExportCsvUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ExportCsvUseCaseTest.kt`

- [ ] **Step 1:** Add a `"diapers"` entry to the returned map (alongside `bottle_feeds`):

```kotlin
"diapers" to buildCsv(
    listOf("id", "timestamp", "type", "notes", "created_at"),
    t.diapers.map {
        listOf(it.id, it.timestamp, it.type, it.notes, it.createdAt)
    },
),
```

- [ ] **Step 2: Test** — extend `ExportCsvUseCaseTest`: stub `BackupSource.readTracking()` to return a `TrackingSnapshot` with one `DiaperBackup`, assert the result map has a `"diapers"` key whose CSV contains the header row `id,timestamp,type,notes,created_at` and the diaper's values. Follow the existing `bottle_feeds` assertions in that file.
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.export.domain.usecase.ExportCsvUseCaseTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(export): add diapers section to CSV export`

---

### Task 5: PDF report section

**Files:**
- Modify: wherever `PdfReportData` is declared in `app/src/main/java/com/babytracker/export/domain/` (package `com.babytracker.export.domain`; find the file with `rg "data class PdfReportData"`)
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt`

> The PDF report currently summarizes breastfeeding + sleep only. This task adds a diaper section that mirrors the sleep section. **Critical:** `PdfReportGenerator.countPages()` simulates layout and must mirror every `y`-advance in `render()` exactly, or pagination breaks.

- [ ] **Step 1:** In `PdfReportData.kt`, add `val diapers: List<DiaperChange> = emptyList()` to the `PdfReportData` data class (add `import com.babytracker.domain.model.DiaperChange`).
- [ ] **Step 2:** In `GeneratePdfReportUseCase.kt`, inject `DiaperRepository` and fetch the range:

```kotlin
class GeneratePdfReportUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val diaperRepository: DiaperRepository,
    private val renderer: PdfReportRenderer,
) {
    suspend operator fun invoke(range: DateRange): ByteArray {
        val data = PdfReportData(
            range = range,
            breastfeeding = breastfeedingRepository.getCompletedSessionsBetween(range.start, range.end),
            sleep = sleepRepository.getCompletedRecordsBetween(range.start, range.end),
            diapers = diaperRepository.getBetween(range.start, range.end),
        )
        return withContext(Dispatchers.Default) { renderer.render(data) }
    }
}
```

(add `import com.babytracker.domain.repository.DiaperRepository`)

- [ ] **Step 3:** In `PdfReportGenerator.kt`:
  - Add the import `import com.babytracker.domain.model.DiaperChange`.
  - Add a paint (next to `sleepHeaderPaint`):

```kotlin
private val diaperHeaderPaint = Paint().apply {
    color = DIAPER
    textSize = HEADER_SIZE
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
```

  - Add the color token to the companion (next to `SLEEP`):

```kotlin
private const val DIAPER = 0xFF00897B.toInt() // Teal600
```

  - Add a diaper summary line in `drawSummary` (after the sleep summary line, before the closing separator). Replace the block:

```kotlin
        canvas.drawText("Sleep records", MARGIN, y, bodyPaint)
        canvas.drawText(data.sleep.size.toString(), COL_SUMMARY_COUNT, y, bodyBoldPaint)
        canvas.drawText("avg $avgSleepPerDay per day", COL_SUMMARY_AVG, y, captionPaint)
        y += LINE * 0.6f
```

  with:

```kotlin
        canvas.drawText("Sleep records", MARGIN, y, bodyPaint)
        canvas.drawText(data.sleep.size.toString(), COL_SUMMARY_COUNT, y, bodyBoldPaint)
        canvas.drawText("avg $avgSleepPerDay per day", COL_SUMMARY_AVG, y, captionPaint)
        y += LINE

        val avgDiaperPerDay = "%.1f".format(data.diapers.size.toFloat() / rangeDays)
        canvas.drawText("Diaper changes", MARGIN, y, bodyPaint)
        canvas.drawText(data.diapers.size.toString(), COL_SUMMARY_COUNT, y, bodyBoldPaint)
        canvas.drawText("avg $avgDiaperPerDay per day", COL_SUMMARY_AVG, y, captionPaint)
        y += LINE * 0.6f
```

  - Add the diaper detail section in `render()`, immediately after the sleep section's `for` loop and before `drawPageFooter(...)`:

```kotlin
        y += SECTION_GAP

        // Diaper section
        val diaperPos = ensureRoom(doc, page, y, totalPages)
        page = diaperPos.page; canvas = diaperPos.canvas; y = diaperPos.y
        canvas.drawText("Diapers (${data.diapers.size})", MARGIN, y, diaperHeaderPaint)
        y += LINE * 0.7f
        y = drawColumnHeaders(canvas, y, "Date & Time", "Type", "Notes")
        canvas.drawLine(MARGIN, y - LINE * 0.15f, PAGE_WIDTH - MARGIN, y - LINE * 0.15f, separatorPaint)
        y += LINE * 0.5f

        for (d in data.diapers) {
            val pos = ensureRoom(doc, page, y, totalPages) { c ->
                val hy = drawColumnHeaders(c, MARGIN + LINE, "Date & Time", "Type", "Notes")
                c.drawLine(MARGIN, hy - LINE * 0.15f, PAGE_WIDTH - MARGIN, hy - LINE * 0.15f, separatorPaint)
                hy + LINE * 0.5f
            }
            page = pos.page; canvas = pos.canvas; y = pos.y
            y = drawDiaperRow(canvas, y, d)
        }
```

  - Add the row renderer (next to `drawSleepRow`):

```kotlin
private fun drawDiaperRow(canvas: android.graphics.Canvas, startY: Float, d: DiaperChange): Float {
    val type = d.type.name.lowercase().replaceFirstChar { it.uppercase() }
    canvas.drawText(d.timestamp.formatPdfDateTime(), MARGIN, startY, bodyPaint)
    canvas.drawText(type, COL_TYPE, startY, bodyPaint)
    canvas.drawText(d.notes ?: "—", COL_DURATION, startY, bodyPaint)
    return startY + LINE
}
```

  - **Update `drawSummary`'s mirror in `countPages()`.** The summary now advances one extra `LINE` (the diaper line). Change the summary mirror line from:

```kotlin
        y += LINE * 0.6f + LINE + LINE + LINE * 0.6f  // mirrors drawSummary y-advances exactly
```

  to:

```kotlin
        y += LINE * 0.6f + LINE + LINE + LINE + LINE * 0.6f  // mirrors drawSummary (feed, sleep, diaper)
```

  - **Add the diaper section to `countPages()`** after the sleep `repeat` block:

```kotlin
        y += SECTION_GAP

        // Diaper section header
        y = sim(y, MARGIN + LINE)
        y += LINE * 0.7f + LINE + LINE * 0.5f

        repeat(data.diapers.size) {
            y = sim(y, MARGIN + LINE * 2.5f)
            y += LINE
        }
```

- [ ] **Step 4: Test** — `app/src/test/java/com/babytracker/export/data/PdfReportGeneratorTest.kt` likely asserts the output is a non-empty/valid PDF and/or page counts. Extend it so `PdfReportData` includes diapers and the render still produces a valid PDF; if the test asserts an exact page count for a fixture, recompute it with the added diaper rows. Follow the existing assertions in that file.
- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.export.data.PdfReportGeneratorTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(export): add diaper section to PDF report`

---

### Task 6: Full export/import integration check

- [ ] **Step 1: Build + test** `./gradlew build` then `./gradlew test` — expect SUCCESS/PASS across the export module.
- [ ] **Step 2:** Verify (via the existing `ExportBackupUseCaseTest` / `ImportBackupUseCaseTest`, extending if needed) that an export → import round trip preserves diaper rows and that a v3 backup (no `diapers` field) still imports without error (the defaulted empty list).
- [ ] **Step 3: Commit** `test(export): cover diaper export/import round trip`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- JSON backup includes a `diapers` array (format version 4); importing it restores diaper rows with identity dedup (re-import inserts 0).
- A v3 backup (no `diapers`) still imports successfully.
- CSV export contains a `diapers` section with `id,timestamp,type,notes,created_at`.
- The PDF report shows a "Diapers (N)" section and a "Diaper changes" summary line; multi-page reports paginate correctly (`countPages` matches `render`).

## Self-Review Notes

- Spec coverage: JSON backup v4 + restore, CSV, PDF — all covered.
- Type consistency: `DiaperBackup(id, timestamp, type, notes, createdAt)` is used identically in `BackupData`, `BackupConverters`, and CSV. `ImportCounts.diapersInserted` is defaulted so callers reading other fields are unaffected.
- Forward/backward compatibility: `BackupData.diapers` and `TrackingSnapshot.diapers` default to empty, so v3 backups deserialize and older importers ignore the field.
- PDF risk: the `countPages` mirror is updated in two places (summary +1 `LINE`, new diaper section) — both are spelled out in Task 5. The diaper section uses the same per-row advance (`LINE`) and new-page offset (`MARGIN + LINE * 2.5f`) as the sleep section, so the mirror matches.
- `DiaperRepository.getBetween` (plan 1) returns timestamp-DESC; the PDF lists changes newest-first, consistent with the on-screen history.
