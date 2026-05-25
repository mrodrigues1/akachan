# PDF Export — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-36

**Goal:** Generate a formatted feeding + sleep summary PDF for a selectable date range (7d / 14d / 30d / custom), intended for pediatric appointments, using `android.graphics.pdf.PdfDocument` + Canvas with the app's design tokens.

**Architecture:** `GeneratePdfReportUseCase(range)` (pure domain) pulls completed breastfeeding + sleep records in range via the existing repositories, hands them to a `PdfReportRenderer` (domain interface) whose `PdfReportGenerator` implementation (`export/data`, Android/Canvas) renders an A4 PDF with manual pagination, and returns the rendered **`ByteArray`**. Persisting/sharing the bytes (cache file + `ACTION_SEND`) is the UI's job (PR4) via `BackupFileWriter.writeCacheBytes` (added here). This mirrors PR1: use cases produce content, the UI layer performs the durable/transient write. Keeping the use case at `ByteArray` keeps `export/domain` free of `android.net.Uri`/`FileProvider`, satisfying the layer-isolation rule landed in PR1.

**Tech Stack:** Kotlin 2.3.20, `android.graphics.pdf.PdfDocument` + `Canvas`/`Paint` (no new deps), Room 2.8.4, Hilt, JUnit 5 + MockK + Robolectric (JUnit4 via the Vintage engine added in PR1).

**Dependencies:** PR1 (`feat/export-infra-json-csv`) — uses the FileProvider, `BackupFileWriter`, the JUnit4/Vintage + `androidx.test:core` test setup, and the `export/domain` isolation arch rule. **Merge PR1 first.**

**Suggested implementation branch:** `feat/pdf-export`

---

## Background: verified codebase facts

- `BreastfeedingRepository` exposes `getAllSessions()`, `getActiveSession()`, `getLastSession()`, `getRecentSessions(limit)`, plus insert/update/delete — **no** date-range read. We add one.
- `SleepRepository` already has `suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord>` (backed by `SleepDao.getCompletedRecordsSince(sinceMillis)` → `WHERE start_time >= :sinceMillis AND end_time IS NOT NULL ORDER BY start_time ASC`). We add a bounded `Between` variant for custom ranges with an end.
- Domain models (pure, `java.time`):
  - `BreastfeedingSession`: `startTime: Instant`, `endTime: Instant?`, `startingSide: BreastSide`, `switchTime: Instant?`, `pausedDurationMs: Long`, plus computed `activeDuration: Duration?` and `activeDurationUntil(until)` (subtracts `pausedDurationMs`). Use `activeDuration` for completed sessions.
  - `SleepRecord`: `startTime: Instant`, `endTime: Instant?`, `sleepType: SleepType`, plus computed `duration: Duration?`.
- `util/DateTimeExt.kt` provides `Instant.formatDateTime(): String`, `Instant.formatTime(): String`, `Duration.formatDuration(): String`, `List<T>.groupByLocalDate(keySelector): Map<LocalDate, List<T>>`.
- Entities map to domain via existing `toDomain()` extension functions in `data/local/entity/`.
- Repository impls (`data/repository/`) inject the DAOs and map entity↔domain. `RepositoryModule` binds interfaces → impls (`@Binds @Singleton`).
- Design tokens (`ui/theme/Color.kt`, as `androidx.compose.ui.graphics.Color`): `Pink700 = 0xFFC2185B` (feeding), `Blue700 = 0xFF1976D2` (sleep), `OnSurfaceDark = 0xFF1A1A1A` (body text), `OnSurfaceVariantGrey = 0xFF6D6A64` (captions), `SurfaceYellow = 0xFFFFFDE7` (page background). The PDF renderer re-declares these as `android.graphics.Color` ints (it cannot depend on Compose `Color`).
- Typography sizes (`AkachanTypography`): headlineMedium 28sp, titleLarge 22sp, bodyLarge 16sp, bodySmall 12sp. The renderer uses these as PDF **points** (PdfDocument canvas is 72dpi, 1pt ≈ 1px).
- PR1 added the JUnit4 Vintage engine + `androidx.test:core` to the unit-test classpath, so `@RunWith(RobolectricTestRunner::class)` JVM tests are discovered.

---

## File Structure

**Create:**
- `app/src/main/java/com/babytracker/export/domain/model/DateRange.kt` — `DateRange(start, end)` + preset factory. Pure.
- `app/src/main/java/com/babytracker/export/domain/PdfReportRenderer.kt` — renderer interface + `PdfReportData` holder. Pure.
- `app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt` — `PdfDocument` + Canvas impl; pure `paginate(...)` helper.
- `app/src/main/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCase.kt` — range → records → renderer → `ByteArray`.
- Tests under `app/src/test/java/com/babytracker/export/`.

**Modify:**
- `app/src/main/java/com/babytracker/data/local/dao/BreastfeedingDao.kt` — add `getCompletedSessionsBetween(start, end)`.
- `app/src/main/java/com/babytracker/data/local/dao/SleepDao.kt` — add `getCompletedRecordsBetween(start, end)`.
- `app/src/main/java/com/babytracker/domain/repository/BreastfeedingRepository.kt` + `data/repository/BreastfeedingRepositoryImpl.kt` — add `getCompletedSessionsBetween`.
- `app/src/main/java/com/babytracker/domain/repository/SleepRepository.kt` + `data/repository/SleepRepositoryImpl.kt` — add `getCompletedRecordsBetween`.
- `app/src/main/java/com/babytracker/export/di/ExportModule.kt` — bind `PdfReportRenderer` → `PdfReportGenerator`.
- `app/src/main/java/com/babytracker/export/data/BackupFileWriter.kt` — add `suspend writeCacheBytes(fileName, bytes): Uri`.

---

## Task 1: Date-range queries on DAOs + repositories

**Files:**
- Modify: `BreastfeedingDao.kt`, `SleepDao.kt`, both repository interfaces + impls
- Test: `app/src/test/java/com/babytracker/data/local/dao/DateRangeQueriesTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric, JVM)**

```kotlin
package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateRangeQueriesTest {

    private lateinit var db: BabyTrackerDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    // Range semantics: a completed record is included if it OVERLAPS [start,end] at all
    // (start <= end AND end >= start), so an overnight sleep that begins before the window and
    // ends inside it is not silently dropped from a doctor's summary.

    @Test
    fun `breastfeeding between includes overlapping completed rows and excludes others`() = runTest {
        // fully inside
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 100, endTime = 200, startingSide = "LEFT"),
        )
        // starts before window, ends inside -> INCLUDED (overlap)
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = -50, endTime = 50, startingSide = "RIGHT"),
        )
        // in-progress (endTime null) -> excluded
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 150, endTime = null, startingSide = "RIGHT"),
        )
        // fully after window -> excluded
        db.breastfeedingDao().insertSession(
            BreastfeedingEntity(startTime = 5000, endTime = 6000, startingSide = "LEFT"),
        )
        val result = db.breastfeedingDao().getCompletedSessionsBetween(0, 1000)
        assertEquals(2, result.size)
        assertEquals(listOf(-50L, 100L), result.map { it.startTime })
    }

    @Test
    fun `sleep between includes overnight record crossing the start boundary`() = runTest {
        // overnight: starts before window, ends inside -> INCLUDED
        db.sleepDao().insertRecord(SleepEntity(startTime = -100, endTime = 300, sleepType = "NIGHT_SLEEP"))
        // fully inside
        db.sleepDao().insertRecord(SleepEntity(startTime = 400, endTime = 500, sleepType = "NAP"))
        // in-progress -> excluded
        db.sleepDao().insertRecord(SleepEntity(startTime = 150, endTime = null, sleepType = "NAP"))
        // fully after -> excluded
        db.sleepDao().insertRecord(SleepEntity(startTime = 5000, endTime = 6000, sleepType = "NAP"))
        val result = db.sleepDao().getCompletedRecordsBetween(0, 1000)
        assertEquals(2, result.size)
        assertEquals(listOf(-100L, 400L), result.map { it.startTime })
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.local.dao.DateRangeQueriesTest" -PfastTests`
Expected: FAIL — unresolved `getCompletedSessionsBetween` / `getCompletedRecordsBetween`.

- [ ] **Step 3: Add the DAO queries**

Overlap semantics: include any completed record whose `[start_time, end_time]` intersects `[startMillis, endMillis]` — i.e. `start_time <= endMillis AND end_time >= startMillis` — so cross-boundary records (e.g. an overnight sleep starting before the window) are not dropped.

`BreastfeedingDao.kt`:
```kotlin
@Query(
    "SELECT * FROM breastfeeding_sessions " +
        "WHERE end_time IS NOT NULL AND start_time <= :endMillis AND end_time >= :startMillis " +
        "ORDER BY start_time ASC",
)
suspend fun getCompletedSessionsBetween(startMillis: Long, endMillis: Long): List<BreastfeedingEntity>
```

`SleepDao.kt`:
```kotlin
@Query(
    "SELECT * FROM sleep_records " +
        "WHERE end_time IS NOT NULL AND start_time <= :endMillis AND end_time >= :startMillis " +
        "ORDER BY start_time ASC",
)
suspend fun getCompletedRecordsBetween(startMillis: Long, endMillis: Long): List<SleepEntity>
```

- [ ] **Step 4: Add the repository methods**

`BreastfeedingRepository.kt` interface — add:
```kotlin
suspend fun getCompletedSessionsBetween(start: java.time.Instant, end: java.time.Instant): List<BreastfeedingSession>
```
`BreastfeedingRepositoryImpl.kt` — implement (mirror existing mapping style):
```kotlin
override suspend fun getCompletedSessionsBetween(
    start: Instant,
    end: Instant,
): List<BreastfeedingSession> =
    dao.getCompletedSessionsBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }
```
`SleepRepository.kt` interface — add:
```kotlin
suspend fun getCompletedRecordsBetween(start: Instant, end: Instant): List<SleepRecord>
```
`SleepRepositoryImpl.kt` — implement:
```kotlin
override suspend fun getCompletedRecordsBetween(start: Instant, end: Instant): List<SleepRecord> =
    dao.getCompletedRecordsBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }
```

> Use the exact DAO property name (`dao`, `breastfeedingDao`, `sleepDao`, etc.) that the existing impl already injects — match it, don't introduce a new field. Add `import java.time.Instant` where needed.

- [ ] **Step 5: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.data.local.dao.DateRangeQueriesTest" -PfastTests`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/data/local/dao/ app/src/main/java/com/babytracker/domain/repository/ app/src/main/java/com/babytracker/data/repository/ app/src/test/java/com/babytracker/data/local/dao/DateRangeQueriesTest.kt
git commit -m "feat(db): add completed date-range queries for breastfeeding and sleep"
```

---

## Task 2: DateRange domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/model/DateRange.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/model/DateRangeTest.kt`

`DateRange` is an inclusive `[start, end]` instant window. Presets build a window ending "now" and starting N days earlier; custom takes explicit bounds.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class DateRangeTest {

    @Test
    fun `lastDays builds an inclusive window ending now`() {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val range = DateRange.lastDays(7, now)
        assertEquals(now, range.end)
        assertEquals(Duration.ofDays(7), Duration.between(range.start, range.end))
    }

    @Test
    fun `custom rejects start after end`() {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(start = now, end = now.minusSeconds(1))
        }
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.model.DateRangeTest" -PfastTests`
Expected: FAIL — unresolved `DateRange`.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.domain.model

import java.time.Duration
import java.time.Instant

data class DateRange(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(!start.isAfter(end)) { "DateRange start ($start) must not be after end ($end)" }
    }

    companion object {
        fun lastDays(days: Long, now: Instant = Instant.now()): DateRange =
            DateRange(start = now.minus(Duration.ofDays(days)), end = now)
    }
}
```

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.model.DateRangeTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/model/DateRange.kt app/src/test/java/com/babytracker/export/domain/model/DateRangeTest.kt
git commit -m "feat(export): add DateRange model with preset factory"
```

---

## Task 3: PdfReportRenderer interface + PdfReportData (pure domain)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/PdfReportRenderer.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.babytracker.export.domain

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.export.domain.model.DateRange

data class PdfReportData(
    val range: DateRange,
    val breastfeeding: List<BreastfeedingSession>,
    val sleep: List<SleepRecord>,
)

/** Renders the report to PDF bytes. Implemented in export/data (Android Canvas). */
interface PdfReportRenderer {
    fun render(data: PdfReportData): ByteArray
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/PdfReportRenderer.kt
git commit -m "feat(export): add PdfReportRenderer domain interface"
```

---

## Task 4: PdfReportGenerator (Canvas implementation)

**Files:**
- Create: `app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt`
- Test: `app/src/test/java/com/babytracker/export/data/PdfReportGeneratorTest.kt`

A4 page (595×842 pt). Manual pagination: a `y` cursor advances per drawn row; when it would exceed the bottom margin, the current page is finished and a new one started. Pagination math is extracted into a **pure** `paginate(rowCount, rowsPerPage)` function so page count is unit-testable without Robolectric.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.babytracker.export.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.model.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class PdfReportGeneratorTest {

    private val generator = PdfReportGenerator()

    @Test
    fun `paginate computes page count from row count`() {
        assertEquals(1, PdfReportGenerator.paginate(rowCount = 0, rowsPerPage = 30))
        assertEquals(1, PdfReportGenerator.paginate(rowCount = 30, rowsPerPage = 30))
        assertEquals(2, PdfReportGenerator.paginate(rowCount = 31, rowsPerPage = 30))
    }

    @Test
    fun `render produces non-empty PDF bytes`() {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val data = PdfReportData(
            range = DateRange.lastDays(7, now),
            breastfeeding = listOf(
                BreastfeedingSession(
                    id = 1, startTime = now.minusSeconds(3600), endTime = now.minusSeconds(2400),
                    startingSide = BreastSide.LEFT,
                ),
            ),
            sleep = listOf(
                SleepRecord(
                    id = 1, startTime = now.minusSeconds(7200), endTime = now.minusSeconds(3600),
                    sleepType = SleepType.NAP,
                ),
            ),
        )
        val bytes = generator.render(data)
        assertTrue("PDF bytes should be non-empty", bytes.isNotEmpty())
        // Valid PDFs start with the "%PDF" magic header.
        assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
    }
}
```

> If `android.graphics.pdf.PdfDocument.writeTo` proves unsupported under the project's Robolectric SDK and the `%PDF` assertion fails at runtime (not compile), keep the pure `paginate` test as the authoritative coverage and downgrade the byte test to an `org.junit.Assume.assumeTrue` guard rather than deleting it — do not weaken `paginate`. Verify on first run before deciding.

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.PdfReportGeneratorTest" -PfastTests`
Expected: FAIL — unresolved `PdfReportGenerator`.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.util.formatDateTime
import com.babytracker.util.formatDuration
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportGenerator @Inject constructor() : PdfReportRenderer {

    override fun render(data: PdfReportData): ByteArray {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(newPageInfo(pageNumber))
        var canvas = page.canvas
        var y = MARGIN + TITLE_SIZE

        // Report title + range subtitle.
        canvas.drawText("Baby Tracker — Health Summary", MARGIN, y, titlePaint)
        y += LINE
        canvas.drawText(
            "${data.range.start.formatDateTime()}  to  ${data.range.end.formatDateTime()}",
            MARGIN, y, captionPaint,
        )
        y += SECTION_GAP

        // Feeding section (Pink).
        y = ensureRoom(doc, page, y).let { (p, c, ny) -> page = p; canvas = c; ny }
        canvas.drawText("Feeding (${data.breastfeeding.size})", MARGIN, y, feedingHeaderPaint)
        y += LINE
        for (s in data.breastfeeding) {
            val pos = ensureRoom(doc, page, y); page = pos.page; canvas = pos.canvas; y = pos.y
            canvas.drawText(feedingRow(s), MARGIN, y, bodyPaint)
            y += LINE
        }
        y += SECTION_GAP

        // Sleep section (Blue).
        ensureRoom(doc, page, y).let { (p, c, ny) -> page = p; canvas = c; y = ny }
        canvas.drawText("Sleep (${data.sleep.size})", MARGIN, y, sleepHeaderPaint)
        y += LINE
        for (r in data.sleep) {
            val pos = ensureRoom(doc, page, y); page = pos.page; canvas = pos.canvas; y = pos.y
            canvas.drawText(sleepRow(r), MARGIN, y, bodyPaint)
            y += LINE
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private data class PagePos(val page: PdfDocument.Page, val canvas: android.graphics.Canvas, val y: Float)

    /** Starts a fresh page when the cursor passes the bottom margin. */
    private fun ensureRoom(doc: PdfDocument, current: PdfDocument.Page, y: Float): PagePos {
        if (y <= PAGE_HEIGHT - MARGIN) return PagePos(current, current.canvas, y)
        // PdfDocument.Page has no pageNumber; the number lives on its PageInfo.
        val next = current.info.pageNumber + 1
        doc.finishPage(current)
        val page = doc.startPage(newPageInfo(next))
        return PagePos(page, page.canvas, MARGIN + LINE)
    }

    private fun newPageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create()

    private fun feedingRow(s: BreastfeedingSession): String {
        val duration = s.activeDuration?.formatDuration() ?: "—"
        return "${s.startTime.formatDateTime()}   ${s.startingSide.name.lowercase()}   $duration"
    }

    private fun sleepRow(r: SleepRecord): String {
        val duration = r.duration?.formatDuration() ?: "—"
        return "${r.startTime.formatDateTime()}   ${r.sleepType.name.lowercase()}   $duration"
    }

    private val titlePaint = Paint().apply { color = ON_SURFACE; textSize = TITLE_SIZE; isFakeBoldText = true }
    private val captionPaint = Paint().apply { color = ON_SURFACE_VARIANT; textSize = CAPTION_SIZE }
    private val feedingHeaderPaint = Paint().apply { color = FEEDING; textSize = HEADER_SIZE; isFakeBoldText = true }
    private val sleepHeaderPaint = Paint().apply { color = SLEEP; textSize = HEADER_SIZE; isFakeBoldText = true }
    private val bodyPaint = Paint().apply { color = ON_SURFACE; textSize = BODY_SIZE }

    companion object {
        // Design tokens re-declared as android.graphics.Color ints (cannot import Compose Color).
        private const val FEEDING = 0xFFC2185B.toInt()           // Pink700
        private const val SLEEP = 0xFF1976D2.toInt()             // Blue700
        private const val ON_SURFACE = 0xFF1A1A1A.toInt()        // OnSurfaceDark
        private const val ON_SURFACE_VARIANT = 0xFF6D6A64.toInt() // OnSurfaceVariantGrey

        private const val PAGE_WIDTH = 595f   // A4 @ 72dpi
        private const val PAGE_HEIGHT = 842f
        private const val MARGIN = 40f
        private const val LINE = 22f
        private const val SECTION_GAP = 28f
        private const val TITLE_SIZE = 28f
        private const val HEADER_SIZE = 18f
        private const val BODY_SIZE = 14f
        private const val CAPTION_SIZE = 12f

        /** Pure pagination helper: pages needed for [rowCount] rows at [rowsPerPage]. Always >= 1. */
        fun paginate(rowCount: Int, rowsPerPage: Int): Int =
            if (rowCount <= 0) 1 else (rowCount + rowsPerPage - 1) / rowsPerPage
    }
}
```

> `Color` import is `android.graphics.Color` — never `androidx.compose.ui.graphics.Color`. The `formatDateTime`/`formatDuration` imports are the existing `com.babytracker.util` extensions. If the existing import paths differ (e.g. a different package), match what `DateTimeExt.kt` actually declares.

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.PdfReportGeneratorTest" -PfastTests`
Expected: PASS (`paginate` test always; `%PDF` test if PdfDocument is supported — see Step 1 note).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt app/src/test/java/com/babytracker/export/data/PdfReportGeneratorTest.kt
git commit -m "feat(export): add PdfReportGenerator with manual pagination and design tokens"
```

---

## Task 5: Bind PdfReportRenderer in ExportModule

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/di/ExportModule.kt`

- [ ] **Step 1: Add the binding**

In the `abstract class ExportModule` body (next to `bindBackupSource`):
```kotlin
@Binds
@Singleton
abstract fun bindPdfReportRenderer(impl: PdfReportGenerator): PdfReportRenderer
```
Add imports:
```kotlin
import com.babytracker.export.data.PdfReportGenerator
import com.babytracker.export.domain.PdfReportRenderer
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -PfastTests`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/export/di/ExportModule.kt
git commit -m "feat(export): bind PdfReportRenderer to PdfReportGenerator"
```

---

## Task 6: GeneratePdfReportUseCase

**Files:**
- Create: `app/src/main/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCaseTest.kt`

Pulls completed records in range from both repositories, builds `PdfReportData`, renders to bytes. Pure domain (repositories + renderer interface).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.export.domain.model.DateRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GeneratePdfReportUseCaseTest {

    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var sleepRepository: SleepRepository
    private lateinit var renderer: PdfReportRenderer
    private lateinit var useCase: GeneratePdfReportUseCase

    @BeforeEach
    fun setup() {
        breastfeedingRepository = mockk()
        sleepRepository = mockk()
        renderer = mockk()
        useCase = GeneratePdfReportUseCase(breastfeedingRepository, sleepRepository, renderer)
    }

    @Test
    fun `loads range from both repos and renders bytes`() = runTest {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val range = DateRange.lastDays(7, now)
        val sessions = listOf(
            BreastfeedingSession(
                id = 1, startTime = now.minusSeconds(3600), endTime = now.minusSeconds(2400),
                startingSide = BreastSide.LEFT,
            ),
        )
        coEvery { breastfeedingRepository.getCompletedSessionsBetween(range.start, range.end) } returns sessions
        coEvery { sleepRepository.getCompletedRecordsBetween(range.start, range.end) } returns emptyList()

        val captured = slot<PdfReportData>()
        every { renderer.render(capture(captured)) } returns byteArrayOf(1, 2, 3)

        val result = useCase(range)

        assertEquals(listOf<Byte>(1, 2, 3), result.toList())
        assertEquals(sessions, captured.captured.breastfeeding)
        assertEquals(range, captured.captured.range)
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.GeneratePdfReportUseCaseTest" -PfastTests`
Expected: FAIL — unresolved `GeneratePdfReportUseCase`.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.export.domain.usecase

import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.export.domain.PdfReportData
import com.babytracker.export.domain.PdfReportRenderer
import com.babytracker.export.domain.model.DateRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GeneratePdfReportUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val renderer: PdfReportRenderer,
) {
    suspend operator fun invoke(range: DateRange): ByteArray {
        val data = PdfReportData(
            range = range,
            breastfeeding = breastfeedingRepository.getCompletedSessionsBetween(range.start, range.end),
            sleep = sleepRepository.getCompletedRecordsBetween(range.start, range.end),
        )
        // Canvas/PDF rendering is synchronous CPU work — move it off the caller (likely a
        // Main-dispatched ViewModel coroutine) so a 30-day report never blocks the UI.
        return withContext(Dispatchers.Default) { renderer.render(data) }
    }
}
```

> `kotlinx.coroutines.Dispatchers`/`withContext` are Kotlin (not Android) imports, so the use case stays within the `export/domain` isolation rule. The repository reads are already `suspend` and do their own IO off-thread.
>
> **detekt `InjectDispatcher`:** the config enables this rule (for IO/Default/Unconfined), but it only fires under type-resolution analysis, which the project's `./gradlew detekt` task does not run — the shipped `UpdateChecker` and PR1's `BackupFileWriter` both hardcode `withContext(Dispatchers.*)` and pass the gate. Hardcoding `Dispatchers.Default` here matches that established convention; do **not** introduce a dispatcher-injection DI layer that the rest of the codebase doesn't use. (If a future change turns on type-resolution detekt, inject a qualified dispatcher across all three call sites together.)

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.domain.usecase.GeneratePdfReportUseCaseTest" -PfastTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/GeneratePdfReportUseCaseTest.kt
git commit -m "feat(export): add GeneratePdfReportUseCase producing PDF bytes"
```

---

## Task 7: BackupFileWriter.writeCacheBytes

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/data/BackupFileWriter.kt`
- Test: extend `app/src/test/java/com/babytracker/export/data/BackupFileWriterTest.kt`

PR4 will write the PDF (and zipped CSV) bytes to a transient cache file for `ACTION_SEND`. Add a binary sibling of `writeCacheFile` reusing the same path-traversal guard + unique-dir + IO-dispatcher behavior.

- [ ] **Step 1: Add the failing test**

Append to `BackupFileWriterTest`:
```kotlin
@Test
fun `writeCacheBytes returns readable content uri and rejects traversal`() = runTest {
    val uri = writer.writeCacheBytes("report.pdf", byteArrayOf(37, 80, 68, 70)) // %PDF
    assertEquals("content", uri.scheme)
    val read = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    assertEquals("%PDF", read.decodeToString())

    assertThrows(IllegalArgumentException::class.java) {
        kotlinx.coroutines.runBlocking { writer.writeCacheBytes("../evil.pdf", byteArrayOf(1)) }
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupFileWriterTest" -PfastTests`
Expected: FAIL — unresolved `writeCacheBytes`.

- [ ] **Step 3: Implement — refactor the shared logic**

Replace the body of `writeCacheFile` to delegate to a shared private helper, and add `writeCacheBytes`:

```kotlin
/** Transient path (text): write to cache, return a shareable FileProvider Uri. */
suspend fun writeCacheFile(fileName: String, content: String): Uri =
    writeCacheBytes(fileName, content.toByteArray(Charsets.UTF_8))

/** Transient path (binary, e.g. PDF): write to cache, return a shareable FileProvider Uri. */
suspend fun writeCacheBytes(fileName: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
    require(
        !fileName.contains('/') && !fileName.contains('\\') &&
            fileName != "." && fileName != "..",
    ) { "Invalid export file name: $fileName" }

    val uniqueDir = File(File(context.cacheDir, "exports"), java.util.UUID.randomUUID().toString())
        .apply { mkdirs() }
    val file = File(uniqueDir, fileName)
    require(file.canonicalFile.parentFile == uniqueDir.canonicalFile) {
        "Resolved export path escapes exports dir: $fileName"
    }
    file.writeBytes(bytes)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
```

> This preserves PR1's `writeCacheFile` contract (its existing tests still pass — same unique-dir + traversal guard) and adds the binary path. Keep `writeToUri` unchanged.

- [ ] **Step 4: Run, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.export.data.BackupFileWriterTest" -PfastTests`
Expected: PASS (including the PR1 cache tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/BackupFileWriter.kt app/src/test/java/com/babytracker/export/data/BackupFileWriterTest.kt
git commit -m "feat(export): add writeCacheBytes for binary share artifacts"
```

---

## Task 8: Architecture + full suite

- [ ] **Step 1: Architecture tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.babytracker.architecture.*"`
Expected: PASS. The PR1 `export/domain` isolation rule must still hold — `DateRange`, `PdfReportRenderer`, `PdfReportData`, and `GeneratePdfReportUseCase` import only Kotlin/`java.time`/domain models + repository interfaces; `PdfReportGenerator` (Android/Canvas) lives in `export/data`. If the rule fails, move the offending code into `export/data`.

- [ ] **Step 2: Formatting + static analysis**

Run: `./gradlew ktlintFormat detekt`
Expected: `BUILD SUCCESSFUL`. Fix detekt findings by changing code, never `@Suppress`. (The `render` method may trip a method-length rule; if so, extract `drawFeedingSection`/`drawSleepSection` private helpers rather than suppressing.)

- [ ] **Step 3: Full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 4: Commit any formatting fixes**

```bash
git add -A
git commit -m "style(export): apply ktlint formatting"
```
(Skip if nothing changed.)

---

## Acceptance Criteria

- `BreastfeedingDao`/`SleepDao` + both repositories expose completed-record `Between(start, end)` date-range reads (in-progress and out-of-range rows excluded).
- `DateRange` is a pure domain model with `lastDays(n)` presets and a `start <= end` invariant.
- `PdfReportRenderer` is a pure domain interface; `GeneratePdfReportUseCase(range)` returns rendered `ByteArray` from completed feeding + sleep records, depending only on repositories + the renderer interface.
- `PdfReportGenerator` renders an A4 PDF with manual pagination and the app's design-token colors/sizes, lives in `export/data`, and exposes a pure unit-tested `paginate(...)`.
- `BackupFileWriter.writeCacheBytes` writes binary artifacts to a unique cache dir (traversal-guarded) and returns a FileProvider `Uri`; existing `writeCacheFile` behavior preserved.
- `export/domain` isolation arch rule still passes; `./gradlew :app:testDebugUnitTest`, `ktlintFormat`, `detekt` all pass.

## Notes for downstream PRs

- PR4 (UI) adds a range-picker dialog (7d/14d/30d/custom → `DateRange`), calls `GeneratePdfReportUseCase(range)`, writes the bytes via `BackupFileWriter.writeCacheBytes("baby-report.pdf", bytes)`, and launches `ACTION_SEND` with MIME `application/pdf` + `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData` (see PR1's share-intent grant note).
