package com.babytracker.ui.sleep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SleepTrackingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sleepEntryTapOpensEditAction() {
        val record = SleepRecord(
            id = 10L,
            startTime = Instant.parse("2024-01-01T12:00:00Z"),
            endTime = Instant.parse("2024-01-01T13:00:00Z"),
            sleepType = SleepType.NAP,
        )
        var editedRecord: SleepRecord? = null

        composeRule.setContent {
            BabyTrackerTheme {
                SwipeableSleepEntry(
                    record = record,
                    onDeleteRequest = {},
                    onEditRecord = { editedRecord = it },
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and hasClickAction(),
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(record, editedRecord)
        }
    }

    @Test
    fun sleepEntryShowsEditAffordance() {
        val record = SleepRecord(
            id = 10L,
            startTime = Instant.parse("2024-01-01T12:00:00Z"),
            endTime = Instant.parse("2024-01-01T13:00:00Z"),
            sleepType = SleepType.NAP,
        )

        composeRule.setContent {
            BabyTrackerTheme {
                SwipeableSleepEntry(
                    record = record,
                    onDeleteRequest = {},
                    onEditRecord = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit sleep entry")
            .assertIsDisplayed()
    }

    @Test
    fun addSleepEntrySheetPrimaryActionKeepsMinimumTouchTarget() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddSleepEntrySheetContent(
                    uiState = sheetState(SleepType.NIGHT_SLEEP),
                    onTypeChanged = {},
                    onStartTimeClick = {},
                    onEndTimeClick = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Save Night Sleep")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun editSleepEntrySheetPrimaryActionKeepsMinimumTouchTarget() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddSleepEntrySheetContent(
                    uiState = sheetState(SleepType.NAP),
                    isEditing = true,
                    onTypeChanged = {},
                    onStartTimeClick = {},
                    onEndTimeClick = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Update Nap")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun addSleepEntrySheetDisablesSaveWhenTimesNeedFixing() {
        composeRule.setContent {
            BabyTrackerTheme {
                AddSleepEntrySheetContent(
                    uiState = sheetState(SleepType.NAP).copy(
                        entryDurationPreview = null,
                        entryError = "End time needs to be after start time. Adjust one time to save this sleep.",
                    ),
                    onTypeChanged = {},
                    onStartTimeClick = {},
                    onEndTimeClick = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "End time needs to be after start time. Adjust one time to save this sleep.",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Save Nap")
            .assertIsNotEnabled()
    }

    private fun sheetState(sleepType: SleepType) = SleepUiState(
        entryType = sleepType,
        entryStartTime = LocalTime.of(20, 0),
        entryEndTime = LocalTime.of(22, 0),
        entryDurationPreview = Duration.ofHours(2),
    )
}
