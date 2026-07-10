package com.babytracker.ui.partner

import android.content.Context
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import com.babytracker.sharing.data.firebase.SharedSleepOpStream
import com.babytracker.sharing.data.firebase.deleteSleepOps
import com.babytracker.sharing.data.firebase.observePartnerConnected
import com.babytracker.sharing.data.firebase.observeSleepOps
import com.babytracker.sharing.data.firebase.observeSnapshot
import com.babytracker.sharing.data.firebase.writeSleepOp
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.babytracker.sharing.usecase.EditPartnerFeedUseCase
import com.babytracker.sharing.usecase.LogPartnerFeedUseCase
import com.babytracker.sharing.usecase.ObservePartnerDataUseCase
import com.babytracker.sharing.usecase.ObservePartnerFeedHistoryUseCase
import com.babytracker.sharing.usecase.StartPartnerSleepUseCase
import com.babytracker.sharing.usecase.StopPartnerSleepUseCase
import com.babytracker.sharing.usecase.SubmitFeedOpUseCase
import com.babytracker.sharing.usecase.SubmitSleepOpUseCase
import com.babytracker.sharing.usecase.UpdatePartnerSleepUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import com.babytracker.widget.WidgetUpdater
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun setUpMocks() {
        mockkStatic("com.babytracker.sharing.data.firebase.FirestoreSharingServiceKt")
    }

    @After
    fun tearDownMocks() {
        unmockkAll()
    }

    private val fixedNow = Instant.parse("2026-05-12T06:00:00Z")
    private val fixedNowProvider = { fixedNow.toEpochMilli() }
    private val noOpWidgetUpdater = object : WidgetUpdater { override suspend fun updateAll() = Unit }
    private val appContext: Context
        get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

    private fun buildViewModel(): PartnerDashboardViewModel = buildViewModel(snapshot = null)

    private fun buildViewModel(snapshot: ShareSnapshot?): PartnerDashboardViewModel {
        val service = fakeSharing(snapshot).service
        val settings = FakePartnerSettingsRepository()
        val observeData = ObservePartnerDataUseCase(service, settings, dagger.Lazy { error("debug builder unused") })
        val observeFeed = ObservePartnerFeedHistoryUseCase(service, settings) { fixedNow }
        return PartnerDashboardViewModel(observeData, observeFeed, noOpWidgetUpdater, settings, appContext)
    }

    private fun buildBottleFeedViewModel(): PartnerBottleFeedViewModel {
        val scope = CoroutineScope(SupervisorJob())
        val submitFeedOp = SubmitFeedOpUseCase(fakeSharing(null).service, FakePartnerSettingsRepository(), scope)
        return PartnerBottleFeedViewModel(
            logPartnerFeed = LogPartnerFeedUseCase(submitFeedOp),
            editPartnerFeed = EditPartnerFeedUseCase(submitFeedOp),
            settingsRepository = FakePartnerSettingsRepository(),
            appContext = appContext,
            now = Instant::now,
        )
    }

    private fun buildSleepViewModel(): PartnerSleepViewModel {
        val scope = CoroutineScope(SupervisorJob())
        val submitSleepOp = SubmitSleepOpUseCase(fakeSharing(null).service, FakePartnerSettingsRepository(), scope)
        return PartnerSleepViewModel(
            startSleep = StartPartnerSleepUseCase(submitSleepOp),
            stopSleep = StopPartnerSleepUseCase(submitSleepOp),
            updateSleep = UpdatePartnerSleepUseCase(submitSleepOp),
            service = fakeSharing(null).service,
            sharedSleepOps = mockk { every { observe(any(), any()) } returns emptyFlow() },
            settingsRepository = FakePartnerSettingsRepository(),
            appContext = appContext,
            now = Instant::now,
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.onNode(
            hasText("Mia")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("RECENT FEEDINGS")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).performScrollTo().assertIsDisplayed()
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.onNodeWithText("No feeding history shared").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No sleep record shared").performScrollTo().assertIsDisplayed()
        // The "Latest care" summary panel was removed in the tile redesign, so the only remaining
        // "No allergies shared" copy is the ALLERGIES section empty state.
        composeRule.onAllNodesWithText("No allergies shared").assertCountEquals(1)
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
            sleepType = SleepType.NAP,
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.onNodeWithText("RECENT FEEDINGS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("LAST SLEEP").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ALLERGIES").performScrollTo().assertIsDisplayed()
        // "Fed 25m ago" / "Napped 1h 0m ago" now surface as idle status inside the feeding/sleep tiles.
        composeRule.onNodeWithText("Fed 25m ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Napped 1h 0m ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("15m 0s").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2h 0m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cow's Milk Protein").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No shared records yet").assertDoesNotExist()
    }

    @Test
    fun idleSleepShowsStartButtons() {
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(makeSnapshot(sleepRecords = emptyList())),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("Sleep. Open sleep screen.").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start Nap").assertIsDisplayed()
        composeRule.onNodeWithText("Start Night Sleep").assertIsDisplayed()
    }

    @Test
    fun activeOwnerSleepShowsStopButButNoEditAffordance() {
        val ownerActive = SleepSnapshot(
            id = 4L,
            startTime = fixedNow.minus(Duration.ofHours(1)).toEpochMilli(),
            endTime = null,
            sleepType = SleepType.NAP,
            notes = null,
            clientId = "owner-cid",
            startedBy = SleepAuthor.OWNER,
        )
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(makeSnapshot(sleepRecords = listOf(ownerActive))),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("Sleep. Open sleep screen.").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Stop Session").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit sleep session").assertDoesNotExist()
    }

    @Test
    fun activePartnerSleepShowsEditAffordance() {
        val partnerActive = SleepSnapshot(
            id = 5L,
            startTime = fixedNow.minus(Duration.ofHours(1)).toEpochMilli(),
            endTime = null,
            sleepType = SleepType.NAP,
            notes = null,
            clientId = "partner-cid",
            startedBy = SleepAuthor.PARTNER,
        )
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(makeSnapshot(sleepRecords = listOf(partnerActive))),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("Sleep. Open sleep screen.").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Edit sleep session").assertIsDisplayed()
    }

    @Test
    fun activeSleepIsPresentedInHeroStatusInsteadOfQuietState() {
        val activeSleep = SleepSnapshot(
            id = 3L,
            startTime = fixedNow.minus(Duration.ofHours(2)).toEpochMilli(),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
            notes = null,
        )

        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = fixedNowProvider,
                viewModel = buildViewModel(
                    makeSnapshot(sleepRecords = listOf(activeSleep)),
                ),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.onNodeWithText("Sleeping when shared").assertIsDisplayed()
        composeRule.onNodeWithText("2h 0m").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet right now").assertDoesNotExist()
        composeRule.onNodeWithText(
            "No active feeding or sleep was shared. Nothing needs attention.",
        ).assertDoesNotExist()
    }

    @Test
    fun activeSleepTimerTicksEverySecondWhileShareIsFresh() {
        val initialNow = Instant.parse("2026-05-12T06:00:00Z")
        var currentTimeMs = initialNow.toEpochMilli()
        val activeSleep = SleepSnapshot(
            id = 3L,
            startTime = initialNow.minus(Duration.ofSeconds(30)).toEpochMilli(),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
            notes = null,
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = { currentTimeMs },
                viewModel = buildViewModel(
                    makeSnapshot(lastSyncAt = initialNow, sleepRecords = listOf(activeSleep)),
                ),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("30s").assertIsDisplayed()

        currentTimeMs += Duration.ofSeconds(2).toMillis()
        composeRule.mainClock.advanceTimeBy(1_001L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("32s").assertIsDisplayed()
        composeRule.onNodeWithText("30s").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun staleActiveSleepTimerStillCountsLiveFromStartAndTicks() {
        // Reproduces the partner dashboard bug: with a share well past the 30-minute staleness
        // threshold, the sleep stopwatch must keep counting from the shared start time (not show 0s,
        // not freeze at the last shared update, and keep ticking every second).
        val initialNow = Instant.parse("2026-05-12T06:00:00Z")
        var currentTimeMs = initialNow.toEpochMilli()
        val activeSleep = SleepSnapshot(
            id = 3L,
            startTime = initialNow.minus(Duration.ofHours(3)).toEpochMilli(),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
            notes = null,
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = { currentTimeMs },
                viewModel = buildViewModel(
                    makeSnapshot(
                        lastSyncAt = initialNow.minus(Duration.ofMinutes(45)),
                        sleepRecords = listOf(activeSleep),
                    ),
                ),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("3h 0m").assertIsDisplayed()

        currentTimeMs += Duration.ofMinutes(1).toMillis()
        composeRule.mainClock.advanceTimeBy(1_001L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("3h 1m").assertIsDisplayed()
        composeRule.onNodeWithText("3h 0m").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun pausedActiveFeedingTimerDoesNotTick() {
        val initialNow = Instant.parse("2026-05-12T06:00:00Z")
        var currentTimeMs = initialNow.toEpochMilli()
        val pausedSession = SessionSnapshot(
            id = 1L,
            startTime = initialNow.minus(Duration.ofMinutes(5)).toEpochMilli(),
            endTime = null,
            startingSide = "LEFT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
            pausedAtMs = initialNow.minus(Duration.ofMinutes(2)).toEpochMilli(),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PartnerDashboardScreen(
                nowProvider = { currentTimeMs },
                viewModel = buildViewModel(
                    makeSnapshot(lastSyncAt = initialNow, sessions = listOf(pausedSession)),
                ),
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("3m 0s").assertIsDisplayed()
        // The status badge must read paused, not "Live", while the timer is frozen.
        composeRule.onNodeWithText("Paused").assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertDoesNotExist()

        currentTimeMs += Duration.ofSeconds(5).toMillis()
        composeRule.mainClock.advanceTimeBy(1_001L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("3m 0s").assertIsDisplayed()
        composeRule.onNodeWithText("3m 5s").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                    bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
                )
            }
        }

        composeRule.onNodeWithText(longName).assertIsDisplayed()
        composeRule.onNodeWithText("Left to Right").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Allergy: $longAllergy")
            .performScrollTo()
            .assertIsDisplayed()
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
                    bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
                )
            }
        }

        composeRule.onNodeWithText("Mia").assertIsDisplayed()
        composeRule.onNodeWithText("Feeding when shared").assertIsDisplayed()
        composeRule.onNodeWithText("This read-only view may be behind.", substring = true).assertIsDisplayed()
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.onNodeWithText("240 ml · 3 bags").performScrollTo().assertIsDisplayed()
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
                bottleFeedViewModel = buildBottleFeedViewModel(),
                sleepViewModel = buildSleepViewModel(),
            )
        }

        composeRule.onNodeWithText("Updated 2h 0m ago").performScrollTo().assertIsDisplayed()
    }

    private class FakeService(val service: FirestoreSharingService)

    private fun fakeSharing(snapshot: ShareSnapshot?): FakeService {
        val service = mockk<FirestoreSharingService>(relaxed = true)
        coEvery { service.signInAnonymously() } returns "partner-uid"
        coEvery { service.isPartnerConnected(any(), any()) } returns true
        coEvery { service.isShareCodeValid(any()) } returns true
        coEvery { service.getPartners(any()) } returns emptyList()
        coEvery { service.fetchSnapshot(any()) } coAnswers { snapshot ?: throw RuntimeException("offline") }
        every { service.observeSnapshot(any()) } returns
            flowOf(com.babytracker.sharing.data.firebase.SnapshotEmission(snapshot, fromCache = false))
        every { service.observePartnerConnected(any(), any()) } returns
            flowOf(com.babytracker.sharing.data.firebase.ConnectionEmission(connected = true, fromCache = false))
        // The live dashboard combines the snapshot with the feed-history flow, which combines the
        // snapshot's feeds with observeFeedOps — so the op listener must emit at least once or the
        // dashboard's combine never produces a value. An empty op list is the no-pending-ops case.
        every { service.observeFeedOps(any(), any()) } returns flowOf(emptyList())
        every { service.observeFeedOps(any()) } returns flowOf(emptyList())
        every { service.observeSleepOps(any(), any()) } returns emptyFlow()
        every { service.observeSleepOps(any()) } returns emptyFlow()
        coEvery { service.writeFeedOp(any(), any(), any()) } just Runs
        coEvery { service.deleteFeedOps(any(), any()) } just Runs
        coEvery { service.writeSleepOp(any(), any(), any()) } just Runs
        coEvery { service.deleteSleepOps(any(), any()) } just Runs
        return FakeService(service)
    }

    private class FakePartnerSettingsRepository : SettingsRepository {
        override fun getThemeConfig(): Flow<ThemeConfig> = flowOf(ThemeConfig.SYSTEM)

        override suspend fun setThemeConfig(themeConfig: ThemeConfig) = Unit

        override fun getVolumeUnit(): Flow<VolumeUnit> = flowOf(VolumeUnit.ML)

        override suspend fun setVolumeUnit(unit: VolumeUnit) = Unit

        override fun getMeasurementSystem(): Flow<MeasurementSystem> = flowOf(MeasurementSystem.METRIC)

        override suspend fun setMeasurementSystem(system: MeasurementSystem) = Unit

        override fun getHomeTileOrder(): Flow<List<HomeTile>> = flowOf(HomeTile.DEFAULT_ORDER)

        override suspend fun setHomeTileOrder(order: List<HomeTile>) = Unit

        override suspend fun clearHomeTileOrder() = Unit

        override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)

        override suspend fun setOnboardingComplete(complete: Boolean) = Unit

        override fun getWakeTime(): Flow<LocalTime?> = flowOf(null)

        override suspend fun setWakeTime(time: LocalTime) = Unit

        override fun getAutoUpdateEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setAutoUpdateEnabled(enabled: Boolean) = Unit

        override fun getRichNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setRichNotificationsEnabled(enabled: Boolean) = Unit

        override fun getPartnerFeedStashNotificationsEnabled(): Flow<Boolean> = flowOf(true)

        override suspend fun setPartnerFeedStashNotificationsEnabled(enabled: Boolean) = Unit

        override fun getAppMode(): Flow<AppMode> = flowOf(AppMode.PARTNER)

        override suspend fun setAppMode(mode: AppMode) = Unit

        override fun getShareCode(): Flow<String?> = flowOf("ABC12345")

        override suspend fun setShareCode(code: String) = Unit

        override suspend fun clearShareCode() = Unit

        override suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean = false

        override fun getQuietHoursStartMinute(): Flow<Int> = flowOf(0)

        override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) = Unit

        override fun getQuietHoursEndMinute(): Flow<Int> = flowOf(480)

        override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) = Unit

        override fun isImportInProgress(): Flow<Boolean> = flowOf(false)

        override suspend fun markImportInProgress(startedAt: Long) = Unit

        override suspend fun restoreFromBackup(data: BackupData) = Unit

        override suspend fun getWidgetPreferences() = com.babytracker.domain.model.WidgetPreferences(
            appMode = AppMode.PARTNER,
            shareCode = "ABC12345",
            enabledFeatures = com.babytracker.domain.model.AppFeature.ALL,
            volumeUnit = VolumeUnit.ML,
        )
    }
}
