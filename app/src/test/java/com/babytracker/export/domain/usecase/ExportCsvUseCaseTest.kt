package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BreastfeedingBackup
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

    private fun tracking(breastfeeding: List<BreastfeedingBackup> = emptyList()) =
        TrackingSnapshot(breastfeeding, emptyList(), emptyList(), emptyList())

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
        assertEquals(setOf("breastfeeding", "sleep", "pumping", "milk_bags"), useCase().keys)
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
