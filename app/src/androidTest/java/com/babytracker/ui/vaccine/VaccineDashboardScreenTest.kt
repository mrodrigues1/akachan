package com.babytracker.ui.vaccine

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class VaccineDashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-06-21T12:00:00Z")

    private val overdue = VaccineRecord(
        id = 1,
        name = "BCG",
        status = VaccineStatus.SCHEDULED,
        scheduledDate = now.minusSeconds(86_400),
        createdAt = now,
    )
    private val future = VaccineRecord(
        id = 2,
        name = "DTaP",
        status = VaccineStatus.SCHEDULED,
        scheduledDate = now.plusSeconds(7 * 86_400),
        createdAt = now,
    )
    private val given = VaccineRecord(
        id = 3,
        name = "MMR",
        status = VaccineStatus.ADMINISTERED,
        administeredDate = now.minusSeconds(172_800),
        createdAt = now,
    )

    private val sameDayDose = VaccineRecord(
        id = 4,
        name = "Rotavirus",
        status = VaccineStatus.SCHEDULED,
        scheduledDate = now.plusSeconds(7 * 86_400),
        createdAt = now,
    )

    private fun populatedState() = VaccineDashboardUiState(
        isLoading = false,
        nextVaccine = future,
        nextVaccines = listOf(future),
        nextInDays = 7,
        mostOverdue = overdue,
        mostOverdueDays = 1,
        overdueCount = 1,
        schedule = listOf(overdue, future),
        recentlyGiven = listOf(given),
        givenCount = 1,
        now = now,
    )

    // No overdue, so the next-up hero claims the whole soonest-day group (DTaP + Rotavirus).
    private fun sameDayNextState() = VaccineDashboardUiState(
        isLoading = false,
        nextVaccine = future,
        nextVaccines = listOf(future, sameDayDose),
        nextInDays = 7,
        schedule = listOf(future, sameDayDose),
        now = now,
    )

    private val toScheduleRecord = VaccineRecord(
        id = 5,
        name = "Hep B",
        status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = now.plusSeconds(20 * 86_400),
        createdAt = now,
    )

    private fun setContent(
        state: VaccineDashboardUiState,
        onAddVaccine: () -> Unit = {},
        onMarkGiven: (VaccineRecord) -> Unit = {},
        onMarkScheduled: (VaccineRecord) -> Unit = {},
        onNavigateToHistory: () -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                VaccineDashboardContent(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onAddVaccine = onAddVaccine,
                    onEditRecord = {},
                    onMarkGiven = onMarkGiven,
                    onMarkScheduled = onMarkScheduled,
                    onDeleteRecord = {},
                    onRetry = {},
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToSettings = {},
                    onNavigateBack = {},
                )
            }
        }
    }

    @Test
    fun firstRunShowsEmptyState() {
        setContent(VaccineDashboardUiState(isLoading = false))
        composeRule.onNodeWithText("Track your baby's vaccines").assertIsDisplayed()
    }

    @Test
    fun overdueHeroAndSectionsRender() {
        setContent(populatedState())
        composeRule.onNodeWithText("Overdue by 1 day").assertIsDisplayed()
        composeRule.onNodeWithText("BCG").assertIsDisplayed() // hero record
        composeRule.onNodeWithText("DTaP").assertIsDisplayed() // remaining schedule
        composeRule.onNodeWithText("MMR").assertIsDisplayed() // recently given
    }

    @Test
    fun heroMarkGivenFiresCallbackWithHeroRecord() {
        var marked: VaccineRecord? = null
        setContent(populatedState(), onMarkGiven = { marked = it })
        composeRule.onNodeWithText("Mark as given").performClick()
        composeRule.runOnIdle { assertEquals(1L, marked?.id) }
    }

    @Test
    fun nextUpHeroListsEverySameDayDose() {
        setContent(sameDayNextState())
        // Both doses on the soonest day appear in the hero; neither is hidden in the schedule list.
        composeRule.onNodeWithText("DTaP").assertIsDisplayed()
        composeRule.onNodeWithText("Rotavirus").assertIsDisplayed()
    }

    @Test
    fun groupedHeroMarkGivenFiresWithThatRowsRecord() {
        var marked: VaccineRecord? = null
        setContent(sameDayNextState(), onMarkGiven = { marked = it })
        composeRule.onNodeWithContentDescription("Mark Rotavirus as given").performClick()
        composeRule.runOnIdle { assertEquals(4L, marked?.id) }
    }

    @Test
    fun toScheduleSectionRendersAndScheduleFiresCallback() {
        var scheduled: VaccineRecord? = null
        setContent(
            VaccineDashboardUiState(isLoading = false, toSchedule = listOf(toScheduleRecord), now = now),
            onMarkScheduled = { scheduled = it },
        )
        // The dashboard section label is uppercased by SectionLabel.
        composeRule.onNodeWithText("TO SCHEDULE").assertIsDisplayed()
        composeRule.onNodeWithText("Hep B").assertIsDisplayed()
        composeRule.onNodeWithText("Schedule").performClick()
        composeRule.runOnIdle { assertEquals(5L, scheduled?.id) }
    }

    @Test
    fun addButtonFiresCallback() {
        var added = false
        setContent(populatedState(), onAddVaccine = { added = true })
        composeRule.onNodeWithText("Add vaccine").performClick()
        composeRule.runOnIdle { assertTrue(added) }
    }

    @Test
    fun viewAllFiresHistoryCallback() {
        var viewedAll = false
        setContent(populatedState(), onNavigateToHistory = { viewedAll = true })
        composeRule.onNodeWithText("View all").performClick()
        composeRule.runOnIdle { assertTrue(viewedAll) }
    }
}
