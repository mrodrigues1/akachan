package com.babytracker.ui.partner

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PartnerDashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): PartnerDashboardViewModel {
        val fetchUseCase = mockk<FetchPartnerDataUseCase>()
        coEvery { fetchUseCase.invoke() } throws RuntimeException("offline")
        return PartnerDashboardViewModel(fetchUseCase)
    }

    private fun buildViewModel(snapshot: ShareSnapshot): PartnerDashboardViewModel {
        val fetchUseCase = mockk<FetchPartnerDataUseCase>()
        coEvery { fetchUseCase.invoke() } returns snapshot
        return PartnerDashboardViewModel(fetchUseCase)
    }

    private fun makeSnapshot(
        lastSyncAt: Instant = Instant.now(),
        sessions: List<SessionSnapshot> = emptyList(),
    ) = ShareSnapshot(
        lastSyncAt = lastSyncAt,
        baby = BabySnapshot("Mia", Instant.now().minus(Duration.ofDays(35)).toEpochMilli(), emptyList()),
        sessions = sessions,
        sleepRecords = emptyList(),
    )

    @Test
    fun settingsIconButtonIsDisplayed() {
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = {},
                viewModel = buildViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun tappingSettingsItemTriggersOnNavigateToSettings() {
        var called = false
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = { called = true },
                viewModel = buildViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(called)
    }

    @Test
    fun dashboardLabelsDataAsReadOnlyAndSharedFromPrimaryDevice() {
        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(makeSnapshot()),
            )
        }

        composeRule.onNodeWithText("Mia").assertIsDisplayed()
        composeRule.onNodeWithText("5w old, read-only partner view").assertIsDisplayed()
        composeRule.onNodeWithText("Primary device last shared:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No shared records yet").assertIsDisplayed()
        composeRule.onNodeWithText(
            "When the primary parent tracks Mia, shared feedings and sleep will appear here.",
        ).assertIsDisplayed()
    }

    @Test
    fun staleActiveSessionCopyDoesNotPresentSharedDataAsLive() {
        val lastSyncAt = Instant.now().minus(Duration.ofMinutes(31))
        val activeSession = SessionSnapshot(
            id = 1L,
            startTime = lastSyncAt.minus(Duration.ofHours(1)).toEpochMilli(),
            endTime = null,
            startingSide = "LEFT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
        )

        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(
                    makeSnapshot(
                        lastSyncAt = lastSyncAt,
                        sessions = listOf(activeSession),
                    ),
                ),
            )
        }

        composeRule.onNodeWithText("This read-only view may be behind.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("ACTIVE WHEN LAST SHARED").assertIsDisplayed()
        composeRule.onNodeWithText("1h 0m").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Read-only estimate from the primary device's last sync",
        ).assertIsDisplayed()
    }
}
