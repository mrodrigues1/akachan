package com.babytracker.ui.partner

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.MergedSleepHistory
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerSleepHistoryUseCase
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import com.babytracker.widget.WidgetUpdater
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PartnerSleepHistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val appContext = ApplicationProvider.getApplicationContext<Context>()

    private fun row(clientId: String, author: SleepAuthor) = SleepSnapshot(
        id = clientId.hashCode().toLong(),
        startTime = Instant.parse("2026-05-12T09:00:00Z").toEpochMilli(),
        endTime = Instant.parse("2026-05-12T10:00:00Z").toEpochMilli(),
        sleepType = SleepType.NAP,
        notes = null,
        clientId = clientId,
        startedBy = author,
    )

    private fun buildHistoryViewModel(rows: List<SleepSnapshot>): PartnerSleepHistoryViewModel {
        val observeData: ObservePartnerDataUseCase = mockk()
        val observe: ObservePartnerSleepHistoryUseCase = mockk()
        every { observeData() } returns flowOf(
            ShareSnapshot(
                lastSyncAt = Instant.EPOCH,
                baby = BabySnapshot("Mia", 0L, emptyList()),
                sessions = emptyList(),
                sleepRecords = rows,
            ),
        )
        coEvery { observe(any()) } returns flowOf(MergedSleepHistory(rows, emptySet()))
        return PartnerSleepHistoryViewModel(observeData, observe, mockk(relaxed = true), appContext)
    }

    private fun buildSleepViewModel(): PartnerSleepViewModel {
        val settings = mockk<com.babytracker.domain.repository.SettingsRepository>()
        every { settings.getShareCode() } returns flowOf(null)
        return PartnerSleepViewModel(
            startSleep = mockk<StartPartnerSleepUseCase>(),
            stopSleep = mockk<StopPartnerSleepUseCase>(),
            updateSleep = mockk<UpdatePartnerSleepUseCase>(),
            service = mockk(relaxed = true),
            sharedSleepOps = mockk { every { observe(any(), any()) } returns emptyFlow() },
            settingsRepository = settings,
            appContext = appContext,
            now = Instant::now,
        )
    }

    private fun renderWith(rows: List<SleepSnapshot>) {
        composeRule.setContent {
            PartnerSleepHistoryScreen(
                onNavigateBack = {},
                viewModel = buildHistoryViewModel(rows),
                sleepViewModel = buildSleepViewModel(),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun partnerRowShowsBadgeAndEditAffordance_ownerRowDoesNot() {
        renderWith(listOf(row("p", SleepAuthor.PARTNER), row("o", SleepAuthor.OWNER)))

        composeRule.onNodeWithText("Partner", substring = true).assertIsDisplayed()
        // Only the partner row exposes the edit affordance.
        composeRule.onAllNodesWithContentDescription("Edit sleep session").assertCountEquals(1)
    }

    @Test
    fun emptyHistoryShowsEmptyState() {
        renderWith(emptyList())

        composeRule.onNodeWithText("No sleep entries yet").assertIsDisplayed()
    }
}
