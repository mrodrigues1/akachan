package com.babytracker.ui.doctorvisit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.ui.home.DoctorVisitHomeCard
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DoctorVisitSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeCardEmptyState() {
        composeRule.setContent {
            BabyTrackerTheme { DoctorVisitHomeCard(summary = DoctorVisitSummary(), onClick = {}) }
        }
        composeRule.onNodeWithText("No visits yet").assertIsDisplayed()
    }

    @Test
    fun homeCardTodayAndOpenQuestions() {
        val summary = DoctorVisitSummary(
            nextUpcoming = DoctorVisit(id = 1, date = Instant.now(), createdAt = Instant.now()),
            openQuestionCount = 2,
        )
        composeRule.setContent {
            BabyTrackerTheme { DoctorVisitHomeCard(summary = summary, onClick = {}) }
        }
        composeRule.onNodeWithText("Visit today").assertIsDisplayed()
        composeRule.onNodeWithText("2 questions to ask").assertIsDisplayed()
    }

    @Test
    fun sheetShowsAddTitleAndSaveInvokesCallback() {
        var saved = false
        composeRule.setContent {
            BabyTrackerTheme {
                DoctorVisitSheet(
                    state = DoctorVisitUiState(),
                    onDateChange = {},
                    onProviderChange = {},
                    onNotesChange = {},
                    onToggleQuestion = {},
                    onAttachSnapshot = {},
                    onViewSnapshot = {},
                    onManageQuestions = {},
                    onSave = { saved = true },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Log a visit").assertIsDisplayed()
        composeRule.onNodeWithTag(DOCTOR_VISIT_SAVE_TAG).performClick()
        composeRule.runOnIdle { assertEquals(true, saved) }
    }
}
