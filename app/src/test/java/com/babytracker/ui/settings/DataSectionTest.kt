package com.babytracker.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.SettingsBackup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class DataSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows import confirm dialog with counts`() {
        val preview = ImportPreview(
            data = mockBackup(), breastfeeding = 3, sleep = 2, pumping = 1, milkBags = 4,
        )
        composeRule.setContent {
            MaterialTheme {
                DataSection(
                    state = DataExportUiState(importPreview = preview),
                    onSavePdf = {}, onSharePdf = {}, onExportJson = {}, onExportCsv = {},
                    onImport = {}, onConfirmImport = {}, onCancelImport = {},
                )
            }
        }
        composeRule.onNodeWithTag("importConfirmText").assertIsDisplayed()
        composeRule.onNodeWithText("3 feeding sessions", substring = true).assertIsDisplayed()
    }

    @Test
    fun `shows incomplete-import recovery notice`() {
        composeRule.setContent {
            MaterialTheme {
                DataSection(
                    state = DataExportUiState(importIncomplete = true),
                    onSavePdf = {}, onSharePdf = {}, onExportJson = {}, onExportCsv = {},
                    onImport = {}, onConfirmImport = {}, onCancelImport = {},
                )
            }
        }
        composeRule.onNodeWithTag("importIncompleteNotice").assertIsDisplayed()
    }

    private fun mockBackup() = BackupData(
        backupFormatVersion = 1,
        roomSchemaVersion = 3,
        appVersion = "1.0.0",
        exportedAt = 0,
        baby = null,
        settings = SettingsBackup("SYSTEM", 0, 0, null, true, true, false, 15, 0, 480, false, 60),
        breastfeeding = emptyList(),
        sleep = emptyList(),
        pumping = emptyList(),
        milkBags = emptyList(),
    )
}
