package com.babytracker.ui.partner

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class PartnerDashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): PartnerDashboardViewModel {
        return PartnerDashboardViewModel(buildFetchUseCase(snapshot = null))
    }

    private fun buildViewModel(snapshot: ShareSnapshot): PartnerDashboardViewModel {
        return PartnerDashboardViewModel(buildFetchUseCase(snapshot))
    }

    private fun buildFetchUseCase(
        snapshot: ShareSnapshot?,
        sharingRepository: FakeSharingRepository = FakeSharingRepository(snapshot),
    ): FetchPartnerDataUseCase {
        return FetchPartnerDataUseCase(
            sharingRepository = sharingRepository,
            settingsRepository = FakePartnerSettingsRepository(),
        )
    }

    private fun makeSnapshot(
        lastSyncAt: Instant = Instant.now(),
        allergies: List<String> = emptyList(),
        sessions: List<SessionSnapshot> = emptyList(),
        sleepRecords: List<SleepSnapshot> = emptyList(),
    ) = ShareSnapshot(
        lastSyncAt = lastSyncAt,
        baby = BabySnapshot("Mia", Instant.now().minus(Duration.ofDays(35)).toEpochMilli(), allergies),
        sessions = sessions,
        sleepRecords = sleepRecords,
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
        composeRule.onNodeWithText("Shared just now").assertIsDisplayed()
        composeRule.onNodeWithText("No shared records yet").assertIsDisplayed()
        composeRule.onNodeWithText(
            "When the primary parent tracks Mia, shared feedings and sleep will appear here.",
        ).assertIsDisplayed()
    }

    @Test
    fun titleAndSectionsExposeHeadingsForTalkBackNavigation() {
        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(makeSnapshot()),
            )
        }

        composeRule.onNode(
            hasText("Mia")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("RECENT FEEDINGS")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("LAST SLEEP")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptySnapshotShowsStatusAndSectionEmptyStates() {
        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(makeSnapshot()),
            )
        }

        composeRule.onNodeWithText("Quiet right now").assertIsDisplayed()
        composeRule.onNodeWithText("No active feeding was shared. Nothing needs attention.").assertIsDisplayed()
        composeRule.onNodeWithText("No feeding history shared").assertIsDisplayed()
        composeRule.onNodeWithText("No sleep record shared").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("No allergies shared").assertCountEquals(2)
    }

    @Test
    fun snapshotWithSharedRecordsShowsSectionsWithoutGlobalEmptyState() {
        val completedSession = SessionSnapshot(
            id = 1L,
            startTime = Instant.now().minus(Duration.ofMinutes(25)).toEpochMilli(),
            endTime = Instant.now().minus(Duration.ofMinutes(10)).toEpochMilli(),
            startingSide = "LEFT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
        )
        val sleepRecord = SleepSnapshot(
            id = 2L,
            startTime = Instant.now().minus(Duration.ofHours(3)).toEpochMilli(),
            endTime = Instant.now().minus(Duration.ofHours(1)).toEpochMilli(),
            sleepType = "NAP",
            notes = null,
        )

        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(
                    makeSnapshot(
                        allergies = listOf("CMPA"),
                        sessions = listOf(completedSession),
                        sleepRecords = listOf(sleepRecord),
                    ),
                ),
            )
        }

        composeRule.onNodeWithText("RECENT FEEDINGS").assertIsDisplayed()
        composeRule.onNodeWithText("LAST SLEEP").assertIsDisplayed()
        composeRule.onNodeWithText("ALLERGIES").assertIsDisplayed()
        composeRule.onNodeWithText("Latest care").assertIsDisplayed()
        composeRule.onNodeWithText("Fed 25m ago").assertIsDisplayed()
        composeRule.onNodeWithText("Napped 1h 0m ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 allergy shared").assertIsDisplayed()
        composeRule.onNodeWithText("15m 0s").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2h 0m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cow's Milk Protein").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No shared records yet").assertDoesNotExist()
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
        composeRule.onNode(
            hasContentDescription("Shared update may be behind", substring = true)
                .and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Shared update may be behind"))
                .and(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Feeding when shared").assertIsDisplayed()
        composeRule.onNodeWithText("1h 0m").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Estimate from the last shared update",
        ).assertIsDisplayed()
    }

    @Test
    fun refreshButtonExposesCurrentStateForAccessibility() {
        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(makeSnapshot()),
            )
        }

        composeRule.onNode(
            hasText("Check shared updates")
                .and(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        "Ready to check shared updates",
                    ),
                ),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pullDownRefreshChecksForSharedUpdates() {
        val sharingRepository = FakeSharingRepository(makeSnapshot())
        val fetchUseCase = buildFetchUseCase(makeSnapshot(), sharingRepository)
        val viewModel = PartnerDashboardViewModel(fetchUseCase)

        composeRule.setContent {
            PartnerDashboardScreen(viewModel = viewModel)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pull down to check for shared updates")
            .performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertTrue(sharingRepository.fetchCount >= 2)
    }

    @Test
    fun staleWarningAppearsWhenVisibleDashboardCrossesThreshold() {
        val initialNow = Instant.parse("2026-05-12T06:00:00Z")
        var currentTimeMs = initialNow.toEpochMilli()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = { currentTimeMs },
                viewModel = buildViewModel(
                    makeSnapshot(lastSyncAt = initialNow.minus(Duration.ofMinutes(29))),
                ),
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("This read-only view may be behind.", substring = true).assertDoesNotExist()

        currentTimeMs += Duration.ofMinutes(2).toMillis()
        composeRule.mainClock.advanceTimeBy(60_001L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("This read-only view may be behind.", substring = true).assertIsDisplayed()
    }

    @Test
    fun allergyChipsAreReadOnlyForAccessibility() {
        composeRule.setContent {
            PartnerDashboardScreen(
                viewModel = buildViewModel(
                    makeSnapshot(allergies = listOf("CMPA")),
                ),
            )
        }

        composeRule.onNodeWithContentDescription("Allergy: Cow's Milk Protein")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasNoClickAction()
    }

    private class FakeSharingRepository(
        private val snapshot: ShareSnapshot?,
    ) : SharingRepository {
        var fetchCount = 0
            private set

        override suspend fun signInAnonymously(): String = "partner-uid"

        override suspend fun createShareDocument(code: ShareCode, ownerUid: String) = Unit

        override suspend fun isShareCodeValid(code: ShareCode): Boolean = true

        override suspend fun syncFullSnapshot(code: ShareCode, snapshot: ShareSnapshot) = Unit

        override suspend fun syncSessions(code: ShareCode, sessions: List<SessionSnapshot>) = Unit

        override suspend fun syncSleepRecords(code: ShareCode, sleepRecords: List<SleepSnapshot>) = Unit

        override suspend fun syncBaby(code: ShareCode, baby: BabySnapshot) = Unit

        override suspend fun registerPartner(code: ShareCode, partnerUid: String) = Unit

        override suspend fun fetchSnapshot(code: ShareCode): ShareSnapshot {
            fetchCount += 1
            return snapshot ?: throw RuntimeException("offline")
        }

        override suspend fun isPartnerConnected(code: ShareCode, partnerUid: String): Boolean = true

        override suspend fun getPartners(code: ShareCode): List<PartnerInfo> = emptyList()

        override suspend fun revokePartner(code: ShareCode, partnerUid: String) = Unit

        override suspend fun deleteShareDocument(code: ShareCode) = Unit
    }

    private class FakePartnerSettingsRepository : SettingsRepository {
        override fun getThemeConfig(): Flow<ThemeConfig> = flowOf(ThemeConfig.SYSTEM)

        override suspend fun setThemeConfig(themeConfig: ThemeConfig) = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)

        override suspend fun setOnboardingComplete(complete: Boolean) = Unit

        override fun getMaxPerBreastMinutes(): Flow<Int> = flowOf(0)

        override suspend fun setMaxPerBreastMinutes(minutes: Int) = Unit

        override fun getMaxTotalFeedMinutes(): Flow<Int> = flowOf(0)

        override suspend fun setMaxTotalFeedMinutes(minutes: Int) = Unit

        override fun getWakeTime(): Flow<LocalTime?> = flowOf(null)

        override suspend fun setWakeTime(time: LocalTime) = Unit

        override fun getAutoUpdateEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setAutoUpdateEnabled(enabled: Boolean) = Unit

        override fun getRichNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setRichNotificationsEnabled(enabled: Boolean) = Unit

        override fun getAppMode(): Flow<AppMode> = flowOf(AppMode.PARTNER)

        override suspend fun setAppMode(mode: AppMode) = Unit

        override fun getShareCode(): Flow<String?> = flowOf("ABC12345")

        override suspend fun setShareCode(code: String) = Unit

        override suspend fun clearShareCode() = Unit
    }
}
