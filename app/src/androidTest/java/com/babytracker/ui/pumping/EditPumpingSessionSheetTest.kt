package com.babytracker.ui.pumping

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class EditPumpingSessionSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private val baseSession = PumpingSession(
        id = 1L,
        startTime = baseNow.minusSeconds(600),
        endTime = baseNow,
        breast = PumpingBreast.LEFT,
        volumeMl = 100,
        notes = "Test note",
    )

    private fun baseState(
        breast: PumpingBreast = PumpingBreast.LEFT,
        volumeMl: String = "100",
        notes: String = "Test note",
        validationError: String? = null,
        deleteConfirm: Boolean = false,
        isDeleting: Boolean = false,
        isSaving: Boolean = false,
    ) = EditPumpingSheetState(
        original = baseSession,
        editedStart = baseSession.startTime,
        editedEnd = baseSession.endTime,
        editedBreast = breast,
        editedVolumeMl = volumeMl,
        editedNotes = notes,
        validationError = validationError,
        deleteConfirm = deleteConfirm,
        isDeleting = isDeleting,
        isSaving = isSaving,
    )

    @Test
    fun volumeFieldPrefilledFromState() {
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("100").assertIsDisplayed()
    }

    @Test
    fun notesFieldPrefilledFromState() {
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Test note").assertIsDisplayed()
    }

    @Test
    fun breastPillRowShowsAllOptions() {
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Left").assertIsDisplayed()
        composeRule.onNodeWithText("Right").assertIsDisplayed()
        composeRule.onNodeWithText("Both").assertIsDisplayed()
    }

    @Test
    fun tappingBreastChipEmitsFieldChangeWithNewBreast() {
        var captured: EditPumpingSheetState? = null
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(breast = PumpingBreast.LEFT),
                    onFieldChange = { transform ->
                        captured = transform(baseState(breast = PumpingBreast.LEFT))
                    },
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Right").performClick()

        composeRule.runOnIdle {
            assertEquals(PumpingBreast.RIGHT, captured?.editedBreast)
        }
    }

    @Test
    fun saveButtonDisabledWhenValidationErrorPresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(validationError = "End must be after start"),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Save changes").assertIsNotEnabled()
    }

    @Test
    fun validationErrorTextIsDisplayed() {
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(validationError = "Volume must be greater than 0"),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Volume must be greater than 0").assertIsDisplayed()
    }

    @Test
    fun tappingDeleteShowsDeleteConfirmRow() {
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Delete this session?").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmRowCallsOnDeleteConfirmed() {
        var confirmed = false
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(deleteConfirm = true),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = { confirmed = true },
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun deleteConfirmCancelCallsOnDeleteCancelled() {
        var cancelled = false
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = baseState(deleteConfirm = true),
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = { cancelled = true },
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun saveButtonEnabledWhenStateIsDirtyAndValid() {
        val dirtyState = baseState().copy(editedVolumeMl = "150")
        composeRule.setContent {
            BabyTrackerTheme {
                EditPumpingSessionSheet(
                    state = dirtyState,
                    onFieldChange = {},
                    onDismiss = {},
                    onSave = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                )
            }
        }

        composeRule.onNodeWithText("Save changes").assertIsEnabled()
    }
}
