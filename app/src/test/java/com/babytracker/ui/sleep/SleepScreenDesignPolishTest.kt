package com.babytracker.ui.sleep

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SleepScreenDesignPolishTest {

    @Test
    fun `quick start actions follow selector touch target height`() {
        val source = sourceFile("SleepTrackingScreen.kt").readText()
        val quickStartRow = Regex("private fun SleepQuickStartRow[\\s\\S]*?private fun ActiveSleepCard")
            .find(source)
            ?.value
            .orEmpty()

        assertTrue(
            quickStartRow.contains("heightIn(min = 88.dp)"),
            "sleep quick-start actions must use the 88dp selector height from the design system"
        )
    }

    @Test
    fun `manual entry time controls expose explicit accessibility labels`() {
        val source = sourceFile("SleepTrackingScreen.kt").readText()

        assertTrue(
            source.contains("R.string.change_start_time"),
            "start time control needs an explicit accessibility label"
        )
        assertTrue(
            source.contains("R.string.change_end_time"),
            "end time control needs an explicit accessibility label"
        )
    }

    @Test
    fun `schedule timeline labels protect compact rows from overflow`() {
        val source = sourceFile("SleepScheduleScreen.kt").readText()
        val labelText = Regex("text = label,[\\s\\S]*?\\)")
            .find(source)
            ?.value
            .orEmpty()

        assertTrue(source.contains("TextOverflow.Ellipsis"), "timeline should use TextOverflow.Ellipsis")
        assertTrue(labelText.contains("maxLines = 1"), "timeline labels should stay on one line")
        assertTrue(
            labelText.contains("overflow = TextOverflow.Ellipsis"),
            "timeline labels should truncate instead of crowding trailing values"
        )
    }

    private fun sourceFile(name: String): File {
        return listOf(
            File("src/main/java/com/babytracker/ui/sleep/$name"),
            File("app/src/main/java/com/babytracker/ui/sleep/$name")
        ).first { it.exists() }
    }
}
