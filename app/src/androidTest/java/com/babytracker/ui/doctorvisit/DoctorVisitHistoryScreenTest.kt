package com.babytracker.ui.doctorvisit

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DoctorVisitHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.ofEpochMilli(1_000_000_000L)
    private val upcoming = DoctorVisit(
        id = 1,
        date = now.plusSeconds(86_400),
        providerName = "Dr. Tanaka",
        notes = "Bring chart",
        snapshotLabel = "Data snapshot",
        snapshotCreatedAt = now,
        createdAt = now,
    )
    private val past = DoctorVisit(id = 2, date = now.minusSeconds(86_400), createdAt = now)

    private fun setContent(
        state: DoctorVisitHistoryUiState,
        onEdit: (DoctorVisit) -> Unit = {},
        onDelete: (DoctorVisit) -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                DoctorVisitHistoryContent(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onAdd = {},
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onNavigateBack = {},
                )
            }
        }
    }

    @Test
    fun showsGroupsBadgeAndChip() {
        setContent(
            DoctorVisitHistoryUiState(
                upcoming = listOf(upcoming),
                past = listOf(past),
                questionCounts = mapOf(1L to 2),
            ),
        )
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Past").assertIsDisplayed()
        composeRule.onNodeWithText("Dr. Tanaka").assertIsDisplayed()
        composeRule.onNodeWithText("Summary").assertIsDisplayed()
        composeRule.onNodeWithText("2 questions").assertIsDisplayed()
    }

    @Test
    fun tappingRowInvokesEdit() {
        var edited: DoctorVisit? = null
        setContent(DoctorVisitHistoryUiState(upcoming = listOf(upcoming)), onEdit = { edited = it })
        composeRule.onNodeWithText("Dr. Tanaka").performClick()
        composeRule.runOnIdle { assertEquals(upcoming, edited) }
    }

    @Test
    fun deleteInvokesCallback() {
        var deleted: DoctorVisit? = null
        setContent(DoctorVisitHistoryUiState(past = listOf(past)), onDelete = { deleted = it })
        composeRule.onNodeWithContentDescription("Delete visit").performClick()
        composeRule.runOnIdle { assertEquals(past, deleted) }
    }

    @Test
    fun emptyStateShown() {
        setContent(DoctorVisitHistoryUiState())
        composeRule.onNodeWithText("No visits yet. Log your first one to keep a record.").assertIsDisplayed()
    }
}
