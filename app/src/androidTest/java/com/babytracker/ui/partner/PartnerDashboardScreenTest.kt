package com.babytracker.ui.partner

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.domain.repository.SharingRepository
import com.babytracker.sharing.usecase.FetchPartnerDataUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import com.babytracker.widget.WidgetUpdater
import kotlinx.coroutines.CompletableDeferred
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

    private val fixedNow = Instant.parse("2026-05-12T06:00:00Z")
    private val fixedNowProvider = { fixedNow.toEpochMilli() }
    private val noOpWidgetUpdater = object : WidgetUpdater { override suspend fun updateAll() = Unit }

    private fun buildViewModel(): PartnerDashboardViewModel {
        return PartnerDashboardViewModel(buildFetchUseCase(snapshot = null), noOpWidgetUpdater, FakePartnerSettingsRepository())
    }

    private fun buildViewModel(snapshot: ShareSnapshot): PartnerDashboardViewModel {
        return PartnerDashboardViewModel(buildFetchUseCase(snapshot), noOpWidgetUpdater, FakePartnerSettingsRepository())
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
        lastSyncAt: Instant = fixedNow,
        babyName: String = "Mia",
        allergies: List<String> = emptyList(),
        sessions: List<SessionSnapshot> = emptyList(),
        sleepRecords: List<SleepSnapshot> = emptyList(),
        inventoryTotalMl: Int? = null,
        inventoryBagCount: Int? = null,
        inventoryUpdatedAt: Long? = null,
    ) = ShareSnapshot(
        lastSyncAt = lastSyncAt,
        baby = BabySnapshot(babyName, fixedNow.minus(Duration.ofDays(35)).toEpochMilli(), allergies),
        sessions = sessions,
        sleepRecords = sleepRecords,
        inventoryTotalMl = inventoryTotalMl,
        inventoryBagCount = inventoryBagCount,
        inventoryUpdatedAt = inventoryUpdatedAt,
    )

    @Test
    fun settingsIconButtonIsDisplayed() {
        composeRule.setContent {
            PartnerDashboardScreen(
                onNavigateToSettings = {},
                nowProvider = fixedNowProvider,
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
                nowProvider = fixedNowProvider,
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
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(makeSnapshot()),
            )
        }

        composeRule.onNodeWithText("Mia").assertIsDisplayed()
        composeRule.onNodeWithText("5 weeks old, read-only partner view").assertIsDisplayed()
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
                nowProvider = fixedNowProvider,
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
                nowProvider = fixedNowProvider,
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
            startTime = fixedNow.minus(Duration.ofMinutes(25)).toEpochMilli(),
            endTime = fixedNow.minus(Duration.ofMinutes(10)).toEpochMilli(),
            startingSide = "LEFT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
        )
        val sleepRecord = SleepSnapshot(
            id = 2L,
            startTime = fixedNow.minus(Duration.ofHours(3)).toEpochMilli(),
            endTime = fixedNow.minus(Duration.ofHours(1)).toEpochMilli(),
            sleepType = "NAP",
            notes = null,
        )

        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
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
        val lastSyncAt = fixedNow.minus(Duration.ofMinutes(31))
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
                nowProvider = fixedNowProvider,
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
                nowProvider = fixedNowProvider,
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
        val viewModel = PartnerDashboardViewModel(fetchUseCase, noOpWidgetUpdater, FakePartnerSettingsRepository())

        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pull down to check for shared updates")
            .performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertTrue(sharingRepository.fetchCount >= 2)
    }

    @Test
    fun refreshingExistingDashboardShowsOneProgressSignal() {
        val releaseRefresh = CompletableDeferred<Unit>()
        val sharingRepository = FakeSharingRepository(
            snapshot = makeSnapshot(),
            beforeFetchReturns = { fetchCount ->
                if (fetchCount == 2) {
                    releaseRefresh.await()
                }
            },
        )
        val viewModel = PartnerDashboardViewModel(buildFetchUseCase(makeSnapshot(), sharingRepository), noOpWidgetUpdater, FakePartnerSettingsRepository())

        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = viewModel,
            )
        }
        composeRule.waitUntil { viewModel.uiState.value.snapshot != null }

        composeRule.runOnUiThread { viewModel.refresh() }
        composeRule.waitUntil { viewModel.uiState.value.isLoading }

        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithText("Checking").assertDoesNotExist()

        releaseRefresh.complete(Unit)
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
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun allergyChipsAreReadOnlyForAccessibility() {
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
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

    @Test
    fun narrowLargeFontDashboardKeepsLongTextUsable() {
        val longName = "Mia Sophia Isabelle Charlotte"
        val longAllergy = "VeryLongAllergyNameFromPrimaryDevice"
        val session = SessionSnapshot(
            id = 1L,
            startTime = fixedNow.minus(Duration.ofMinutes(25)).toEpochMilli(),
            endTime = fixedNow.minus(Duration.ofMinutes(10)).toEpochMilli(),
            startingSide = "LEFT",
            switchTime = fixedNow.minus(Duration.ofMinutes(18)).toEpochMilli(),
            pausedDurationMs = 0L,
            notes = null,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                PartnerDashboardScreen(
                    modifier = Modifier.requiredWidth(320.dp),
                    nowProvider = fixedNowProvider,
                    viewModel = buildViewModel(
                        makeSnapshot(
                            babyName = longName,
                            allergies = listOf(longAllergy),
                            sessions = listOf(session),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText(longName).assertIsDisplayed()
        composeRule.onNodeWithText("Latest care").assertIsDisplayed()
        composeRule.onNodeWithText("Left to Right").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Allergy: $longAllergy")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Check shared updates").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun darkThemeDashboardShowsPartnerStatusAndWarning() {
        val lastSyncAt = fixedNow.minus(Duration.ofMinutes(31))
        val activeSession = SessionSnapshot(
            id = 1L,
            startTime = lastSyncAt.minus(Duration.ofMinutes(20)).toEpochMilli(),
            endTime = null,
            startingSide = "RIGHT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
        )

        composeRule.setContent {
            BabyTrackerTheme(darkTheme = true) {
                PartnerDashboardScreen(
                    nowProvider = fixedNowProvider,
                    viewModel = buildViewModel(
                        makeSnapshot(
                            lastSyncAt = lastSyncAt,
                            sessions = listOf(activeSession),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Mia").assertIsDisplayed()
        composeRule.onNodeWithText("Feeding when shared").assertIsDisplayed()
        composeRule.onNodeWithText("This read-only view may be behind.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Check shared updates").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun inventoryCardIsHiddenWhenBagCountIsZero() {
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(
                    makeSnapshot(
                        inventoryTotalMl = 0,
                        inventoryBagCount = 0,
                    ),
                ),
            )
        }

        composeRule.onNodeWithContentDescription("Milk stash", substring = true).assertDoesNotExist()
    }

    @Test
    fun inventoryCardShowsMlAndBagCountWhenStashIsPresent() {
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(
                    makeSnapshot(
                        inventoryTotalMl = 240,
                        inventoryBagCount = 3,
                    ),
                ),
            )
        }

        composeRule.onNodeWithText("240 ml · 3 bags").assertIsDisplayed()
    }

    @Test
    fun inventoryCardShowsUpdatedLineWhenTimestampIsPresent() {
        val updatedAt = fixedNow.minus(Duration.ofHours(2)).toEpochMilli()
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(
                    makeSnapshot(
                        inventoryTotalMl = 180,
                        inventoryBagCount = 2,
                        inventoryUpdatedAt = updatedAt,
                    ),
                ),
            )
        }

        composeRule.onNodeWithText("Updated 2h 0m ago").assertIsDisplayed()
    }

    private class FakeSharingRepository(
        private val snapshot: ShareSnapshot?,
        private val beforeFetchReturns: suspend (Int) -> Unit = {},
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

        override suspend fun syncInventory(code: ShareCode, fields: InventorySnapshotFields) = Unit

        override suspend fun syncBottleFeeds(
            code: ShareCode,
            bottleFeeds: List<com.babytracker.sharing.domain.model.BottleFeedSnapshot>,
        ) = Unit

        override suspend fun registerPartner(code: ShareCode, partnerUid: String) = Unit

        override suspend fun fetchSnapshot(code: ShareCode): ShareSnapshot {
            fetchCount += 1
            beforeFetchReturns(fetchCount)
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

        override fun getVolumeUnit(): Flow<VolumeUnit> = flowOf(VolumeUnit.ML)

        override suspend fun setVolumeUnit(unit: VolumeUnit) = Unit

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

        override suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean = false

        override fun getPredictiveEnabled(): Flow<Boolean> = flowOf(false)

        override suspend fun setPredictiveEnabled(enabled: Boolean) = Unit

        override fun getPredictiveLeadMinutes(): Flow<Int> = flowOf(15)

        override suspend fun setPredictiveLeadMinutes(minutes: Int) = Unit

        override fun getQuietHoursStartMinute(): Flow<Int> = flowOf(0)

        override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) = Unit

        override fun getQuietHoursEndMinute(): Flow<Int> = flowOf(480)

        override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) = Unit

        override fun getNapReminderEnabled(): Flow<Boolean> = flowOf(false)

        override suspend fun setNapReminderEnabled(enabled: Boolean) = Unit

        override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)

        override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit

        override fun getPredictiveSleepEnabled(): Flow<Boolean> = flowOf(false)
        override suspend fun setPredictiveSleepEnabled(enabled: Boolean) = Unit
        override fun getPredictiveSleepLeadMinutes(): Flow<Int> = flowOf(15)
        override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) = Unit

        override fun isImportInProgress(): Flow<Boolean> = flowOf(false)

        override suspend fun markImportInProgress(startedAt: Long) = Unit

        override suspend fun restoreFromBackup(data: BackupData) = Unit
    }
}
