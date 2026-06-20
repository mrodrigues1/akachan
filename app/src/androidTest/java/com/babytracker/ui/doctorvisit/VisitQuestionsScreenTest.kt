package com.babytracker.ui.doctorvisit

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class VisitQuestionsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val q1 = VisitQuestion(id = 1, text = "Ask about sleep", answered = false, createdAt = Instant.EPOCH)
    private val q2 = VisitQuestion(id = 2, text = "Is the rash normal", answered = true, createdAt = Instant.EPOCH)

    private fun setContent(
        state: VisitQuestionsUiState,
        onAdd: () -> Unit = {},
        onExpand: (VisitQuestion?) -> Unit = {},
        onDelete: (VisitQuestion) -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                VisitQuestionsContent(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onDraftChange = {},
                    onAdd = onAdd,
                    onToggleAnswered = {},
                    onExpand = onExpand,
                    onDelete = onDelete,
                    onNavigateBack = {},
                )
            }
        }
    }

    @Test
    fun emptyStateShown() {
        setContent(VisitQuestionsUiState())
        composeRule.onNodeWithText("No questions yet. Add one to ask at your next visit.").assertIsDisplayed()
    }

    @Test
    fun addButtonDisabledWhenDraftBlank() {
        setContent(VisitQuestionsUiState(draft = ""))
        composeRule.onNodeWithContentDescription("Add").assertIsNotEnabled()
    }

    @Test
    fun addInvokesCallbackWhenDraftPresent() {
        var added = false
        setContent(VisitQuestionsUiState(draft = "New question"), onAdd = { added = true })
        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.runOnIdle { assertEquals(true, added) }
    }

    @Test
    fun tappingRowExpandsQuestion() {
        var expanded: VisitQuestion? = null
        setContent(VisitQuestionsUiState(questions = listOf(q1, q2)), onExpand = { expanded = it })
        composeRule.onNodeWithText("Ask about sleep").performClick()
        composeRule.runOnIdle { assertEquals(q1, expanded) }
    }

    @Test
    fun expandedDialogShowsClose() {
        setContent(VisitQuestionsUiState(questions = listOf(q1), expandedQuestion = q1))
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun deleteInvokesCallback() {
        var deleted: VisitQuestion? = null
        setContent(VisitQuestionsUiState(questions = listOf(q1)), onDelete = { deleted = it })
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.runOnIdle { assertEquals(q1, deleted) }
    }
}
