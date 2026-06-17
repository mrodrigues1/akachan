package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BottleFeedBackup
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.DiaperBackup
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportCsvUseCaseTest {

    private lateinit var source: BackupSource
    private lateinit var useCase: ExportCsvUseCase

    @BeforeEach
    fun setup() {
        source = mockk()
        useCase = ExportCsvUseCase(source)
    }

    private fun tracking(
        breastfeeding: List<BreastfeedingBackup> = emptyList(),
        bottleFeeds: List<BottleFeedBackup> = emptyList(),
        diapers: List<DiaperBackup> = emptyList(),
    ) = TrackingSnapshot(breastfeeding, emptyList(), emptyList(), emptyList(), bottleFeeds, diapers = diapers)

    @Test
    fun `breastfeeding csv has header and escaped notes row`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "had a, \"big\" feed", null, 0),
            ),
        )
        val csv = useCase().getValue("breastfeeding")
        val lines = csv.trim().split("\n")
        assertEquals(
            "id,start_time,end_time,starting_side,switch_time,notes,paused_at,paused_duration_ms",
            lines[0],
        )
        assertTrue(lines[1].contains("\"had a, \"\"big\"\" feed\""))
    }

    @Test
    fun `returns a csv for every table`() = runTest {
        coEvery { source.readTracking() } returns tracking()
        assertEquals(
            setOf("breastfeeding", "sleep", "pumping", "milk_bags", "bottle_feeds", "diapers"),
            useCase().keys,
        )
    }

    @Test
    fun `diapers csv has header and a data row`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            diapers = listOf(
                DiaperBackup(id = 1, timestamp = 1_000, type = "DIRTY", notes = "blowout", createdAt = 2_000),
            ),
        )
        val csv = useCase().getValue("diapers")
        val lines = csv.trim().split("\n")
        assertEquals("id,timestamp,type,notes,created_at", lines[0])
        assertEquals("1,1000,DIRTY,blowout,2000", lines[1])
    }

    @Test
    fun `bottle feeds csv has header and a data row`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            bottleFeeds = listOf(
                BottleFeedBackup(1, 1_000, 120, "BREAST_MILK", 7, "evening", 2_000),
            ),
        )
        val csv = useCase().getValue("bottle_feeds")
        val lines = csv.trim().split("\n")
        assertEquals(
            "id,timestamp,volume_ml,type,linked_milk_bag_id,notes,created_at",
            lines[0],
        )
        assertEquals("1,1000,120,BREAST_MILK,7,evening,2000", lines[1])
    }

    @Test
    fun `neutralizes spreadsheet formula in note field`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "=HYPERLINK(\"http://evil\")", null, 0),
            ),
        )
        val dataRow = useCase().getValue("breastfeeding").trim().split("\n")[1]
        // Leading '=' must be defused with an apostrophe so it is not a live formula,
        // and because the apostrophe-prefixed value contains a comma+quote it is RFC-4180 quoted.
        assertTrue(dataRow.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""))
    }

    @Test
    fun `neutralizes formula hidden behind leading whitespace`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "   +1+1", null, 0),
            ),
        )
        val dataRow = useCase().getValue("breastfeeding").trim().split("\n")[1]
        assertTrue(dataRow.startsWith("1,10,20,LEFT,,'   +1+1"))
    }

    @Test
    fun `quotes notes containing carriage returns and CRLF`() = runTest {
        coEvery { source.readTracking() } returns tracking(
            breastfeeding = listOf(
                BreastfeedingBackup(1, 10, 20, "LEFT", null, "line1\r\nline2\rline3", null, 0),
            ),
        )
        // A bare \r or \r\n inside a note must be wrapped in quotes so a CSV reader
        // does not treat it as a record separator and split one row into several.
        val csv = useCase().getValue("breastfeeding")
        assertTrue(csv.contains("\"line1\r\nline2\rline3\""))
    }
}
