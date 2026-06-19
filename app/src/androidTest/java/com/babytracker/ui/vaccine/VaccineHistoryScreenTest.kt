package com.babytracker.ui.vaccine

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class VaccineHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val now = Instant.ofEpochMilli(1_000_000_000L)

    private val overdue = VaccineRecord(
        id = 1,
        name = "BCG",
        status = VaccineStatus.SCHEDULED,
        scheduledDate = now.minusSeconds(86_400),
        createdAt = now,
    )
    private val given = VaccineRecord(
        id = 2,
        name = "MMR",
        status = VaccineStatus.ADMINISTERED,
        administeredDate = now.minusSeconds(172_800),
        createdAt = now,
    )

    private fun state() = VaccineHistoryUiState(
        upcoming = listOf(overdue),
        administeredByDate = listOf(
            given.administeredDate!!.atZone(zone).toLocalDate() to listOf(given),
        ),
        now = now,
    )

    private fun setContent(onMarkGiven: (Long) -> Unit = {}) {
        composeRule.setContent {
            BabyTrackerTheme {
                VaccineHistoryContent(
                    state = state(),
                    snackbarHostState = SnackbarHostState(),
                    onMarkGiven = onMarkGiven,
                    onEditRecord = {},
                    onDeleteRecord = {},
                    onNavigateBack = {},
                )
            }
        }
    }

    @Test
    fun showsOverdueBadgeForOverdueScheduled() {
        setContent()
        composeRule.onNodeWithText("Overdue").assertIsDisplayed()
        composeRule.onNodeWithText("BCG").assertIsDisplayed()
    }

    @Test
    fun markGivenFiresCallbackWithRecordId() {
        var markedId: Long? = null
        setContent(onMarkGiven = { markedId = it })
        composeRule.onNodeWithText("Mark as given").performClick()
        composeRule.runOnIdle { assertEquals(1L, markedId) }
    }

    @Test
    fun showsAdministeredSectionAndRow() {
        setContent()
        composeRule.onNodeWithText("Administered").assertIsDisplayed()
        composeRule.onNodeWithText("MMR").assertIsDisplayed()
    }
}
