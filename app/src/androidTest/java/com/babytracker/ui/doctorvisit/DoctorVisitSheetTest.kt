package com.babytracker.ui.doctorvisit

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.ui.home.DoctorVisitHomeCard
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    onQuestionDraftChange = {},
                    onAddQuestion = {},
                    onToggleQuestion = {},
                    onAttachSnapshot = {},
                    onViewSnapshot = {},
                    onManageQuestions = {},
                    onSave = { saved = true },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Add visit").assertIsDisplayed()
        composeRule.onNodeWithTag(DOCTOR_VISIT_SAVE_TAG).performClick()
        composeRule.runOnIdle { assertEquals(true, saved) }
    }

    @Test
    fun addSheetShowsInboxQuestions() {
        setSheet(
            DoctorVisitUiState(
                inboxQuestions = listOf(question(1, "Inbox question")),
            ),
        )

        composeRule.onNodeWithText("Inbox question").assertExists()
    }

    @Test
    fun editSheetShowsOnlyAttachedAndNewlySelectedQuestions() {
        setSheet(
            DoctorVisitUiState(
                editingId = 7,
                attachedQuestions = listOf(question(1, "Attached question", 7)),
                inboxQuestions = listOf(
                    question(2, "Unrelated inbox question"),
                    question(3, "New question"),
                ),
                selectedQuestionIds = setOf(1, 3),
            ),
        )

        composeRule.onNodeWithText("Attached question").assertExists()
        composeRule.onNodeWithText("New question").assertExists()
        composeRule.onNodeWithText("Unrelated inbox question").assertDoesNotExist()
    }

    @Test
    fun visitQuestionFieldSubmitsThroughCallback() {
        var submitted = false
        setSheet(
            state = DoctorVisitUiState(questionDraft = "Question"),
            onAddQuestion = { submitted = true },
        )

        composeRule.onNodeWithContentDescription("Add question").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(submitted) }
    }

    @Test
    fun sheetManageQuestionsIsAButton() {
        var managed = false
        setSheet(
            state = DoctorVisitUiState(),
            onManageQuestions = { managed = true },
        )

        composeRule.onNodeWithTag("DoctorVisitManageQuestionsButton")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertTrue(managed) }
    }

    @Test
    fun dashboardManageAllIsAButton() {
        var managed = false
        composeRule.setContent {
            BabyTrackerTheme {
                DoctorVisitDashboardContent(
                    state = DoctorVisitDashboardUiState(
                        isLoading = false,
                        questions = listOf(question(1, "Inbox question")),
                        openQuestionCount = 1,
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onDraftChange = {},
                    onAddQuestion = {},
                    onToggleAnswered = {},
                    onRetry = {},
                    onAddVisit = {},
                    onEditVisit = {},
                    onNavigateToHistory = {},
                    onManageQuestions = { managed = true },
                    onNavigateToSettings = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("DoctorVisitManageAllButton")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertTrue(managed) }
    }

    private fun setSheet(
        state: DoctorVisitUiState,
        onAddQuestion: () -> Unit = {},
        onManageQuestions: () -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                DoctorVisitSheet(
                    state = state,
                    onDateChange = {},
                    onProviderChange = {},
                    onNotesChange = {},
                    onQuestionDraftChange = {},
                    onAddQuestion = onAddQuestion,
                    onToggleQuestion = {},
                    onAttachSnapshot = {},
                    onViewSnapshot = {},
                    onManageQuestions = onManageQuestions,
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun question(
        id: Long,
        text: String,
        visitId: Long? = null,
    ) = VisitQuestion(id = id, text = text, visitId = visitId, createdAt = Instant.EPOCH)
}
