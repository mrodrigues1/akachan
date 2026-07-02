package com.babytracker.ui.diaper

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.EditDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class DiaperHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone: ZoneId = ZoneId.systemDefault()

    private val dirtyDay16 = DiaperChange(
        id = 2,
        timestamp = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant(),
        type = DiaperType.DIRTY,
        notes = "blowout",
        createdAt = Instant.ofEpochMilli(1_000),
    )
    private val wetDay15 = DiaperChange(
        id = 1,
        timestamp = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zone).toInstant(),
        type = DiaperType.WET,
        notes = null,
        createdAt = Instant.ofEpochMilli(500),
    )

    private fun setScreen(changes: List<DiaperChange>) {
        val diaperRepository = mockk<DiaperRepository> { every { observeAll() } returns flowOf(changes) }
        val delete = mockk<DeleteDiaperChangeUseCase>().also { coEvery { it.invoke(any()) } just Runs }
        val log = mockk<LogDiaperChangeUseCase>(relaxed = true)
        val edit = mockk<EditDiaperChangeUseCase>(relaxed = true)
        val historyVm = DiaperHistoryViewModel(diaperRepository, delete, zone)
        val editVm = DiaperViewModel(log, edit, ApplicationProvider.getApplicationContext()) {
            Instant.ofEpochMilli(2_000)
        }
        composeRule.setContent {
            BabyTrackerTheme {
                DiaperHistoryScreen(
                    onNavigateBack = {},
                    historyViewModel = historyVm,
                    editViewModel = editVm,
                )
            }
        }
    }

    @Test
    fun rendersDayHeadersForEachDay() {
        setScreen(listOf(dirtyDay16, wetDay15))

        composeRule.onAllNodesWithText("CHANGE", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("Dirty", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Wet", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapDeleteShowsConfirmationDialog() {
        setScreen(listOf(dirtyDay16, wetDay15))

        composeRule.onNodeWithContentDescription("Delete Dirty change", substring = true).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Delete this diaper change?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Delete this diaper change?").assertIsDisplayed()
    }

    @Test
    fun tapEditIconOpensEditSheet() {
        setScreen(listOf(dirtyDay16, wetDay15))

        composeRule.onNodeWithContentDescription("Edit Dirty change", substring = true).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Edit diaper change").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit diaper change").assertIsDisplayed()
    }
}
