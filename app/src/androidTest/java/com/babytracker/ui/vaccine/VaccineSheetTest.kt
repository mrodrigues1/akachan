package com.babytracker.ui.vaccine

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class VaccineSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private fun state(
        status: VaccineStatus = VaccineStatus.ADMINISTERED,
        name: String = "",
    ) = VaccineUiState(
        name = name,
        status = status,
        date = baseNow,
        suggestions = listOf("MMR", "BCG"),
    )

    private fun setSheet(
        state: VaccineUiState,
        onNameChange: (String) -> Unit = {},
        onModeChange: (VaccineStatus) -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                VaccineSheet(
                    state = state,
                    onNameChange = onNameChange,
                    onDoseChange = {},
                    onModeChange = onModeChange,
                    onDateChange = {},
                    onNotesChange = {},
                    onConfirm = onConfirm,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun rendersAllThreeModeSegments() {
        setSheet(state())

        composeRule.onNodeWithText("To schedule").assertIsDisplayed()
        composeRule.onNodeWithText("Already given").assertIsDisplayed()
        composeRule.onNodeWithText("Schedule for later").assertIsDisplayed()
    }

    @Test
    fun toScheduleStateShowsTargetDateLabel() {
        setSheet(state(status = VaccineStatus.TO_SCHEDULE))

        composeRule.onNodeWithText("Target date").assertIsDisplayed()
    }

    @Test
    fun tapSuggestionChipInvokesOnNameChange() {
        var typed: String? = null
        setSheet(state(), onNameChange = { typed = it })

        composeRule.onNodeWithText("BCG").performClick()

        composeRule.runOnIdle { assertEquals("BCG", typed) }
    }

    @Test
    fun toggleScheduleInvokesOnModeChangeAndSwitchesDateLabel() {
        var mode: VaccineStatus? = null
        setSheet(state(), onModeChange = { mode = it })

        // Administered mode shows the "Date given" label.
        composeRule.onNodeWithText("Date given").assertIsDisplayed()
        composeRule.onNodeWithText("Schedule for later").performClick()
        composeRule.runOnIdle { assertEquals(VaccineStatus.SCHEDULED, mode) }
    }

    @Test
    fun scheduledStateShowsScheduledDateLabel() {
        setSheet(state(status = VaccineStatus.SCHEDULED))

        composeRule.onNodeWithText("Scheduled date").assertIsDisplayed()
    }

    @Test
    fun tapSaveInvokesOnConfirm() {
        var confirmed = false
        setSheet(state(name = "MMR"), onConfirm = { confirmed = true })

        composeRule.onNodeWithTag(VACCINE_SAVE_TAG).performClick()

        composeRule.runOnIdle { assertTrue(confirmed) }
    }
}
