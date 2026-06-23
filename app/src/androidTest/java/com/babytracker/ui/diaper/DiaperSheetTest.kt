package com.babytracker.ui.diaper

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.DiaperType
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DiaperSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private fun state(type: DiaperType = DiaperType.WET) = DiaperUiState(
        type = type,
        timestamp = baseNow,
    )

    private fun setSheet(
        state: DiaperUiState,
        onTypeChange: (DiaperType) -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                DiaperSheet(
                    state = state,
                    onTypeChange = onTypeChange,
                    onTimeChange = {},
                    onNotesChange = {},
                    onConfirm = onConfirm,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun rendersThreeTypeSegments() {
        setSheet(state())

        composeRule.onNodeWithText("Wet").assertIsDisplayed()
        composeRule.onNodeWithText("Dirty").assertIsDisplayed()
        composeRule.onNodeWithText("Both").assertIsDisplayed()
    }

    @Test
    fun tapDirtyInvokesOnTypeChange() {
        var selected: DiaperType? = null
        setSheet(state(), onTypeChange = { selected = it })

        composeRule.onNodeWithText("Dirty").performClick()

        composeRule.runOnIdle { assertEquals(DiaperType.DIRTY, selected) }
    }

    @Test
    fun tapSaveInvokesOnConfirm() {
        var confirmed = false
        setSheet(state(), onConfirm = { confirmed = true })

        composeRule.onNodeWithTag(DIAPER_SAVE_TAG).performClick()

        composeRule.runOnIdle { assertTrue(confirmed) }
    }
}
