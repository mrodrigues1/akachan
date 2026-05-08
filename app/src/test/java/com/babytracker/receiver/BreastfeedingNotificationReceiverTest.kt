package com.babytracker.receiver

import android.content.Context
import android.content.Intent
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BreastfeedingNotificationReceiverTest {

    private lateinit var receiver: BreastfeedingNotificationReceiver
    private lateinit var settingsRepository: SettingsRepository
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() {
        settingsRepository = mockk()
        receiver = BreastfeedingNotificationReceiver()
        receiver.settingsRepository = settingsRepository
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(false)
        mockkObject(NotificationHelper)
        every { NotificationHelper.showSwitchSide(any(), any(), any(), any(), any()) } returns Unit
        every { NotificationHelper.showFeedingLimit(any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun switchSideTypeRichDisabledShowsSwitchSideNotification() = runTest {
        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = 1L,
                currentSide = "LEFT",
                elapsedMinutes = 10,
                maxTotalMinutes = 30,
            ),
        )

        verify { NotificationHelper.showSwitchSide(context, 1L, "LEFT", 10, false) }
    }

    @Test
    fun switchSideTypeRichEnabledPassesRichFlagTrue() = runTest {
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)

        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = 1L,
                currentSide = "LEFT",
                elapsedMinutes = 5,
                maxTotalMinutes = 30,
            ),
        )

        verify { NotificationHelper.showSwitchSide(context, 1L, "LEFT", 5, true) }
    }

    @Test
    fun switchSideTypePassesSessionIdFromIntent() = runTest {
        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = 99L,
                currentSide = "RIGHT",
                elapsedMinutes = 0,
                maxTotalMinutes = 0,
            ),
        )

        verify { NotificationHelper.showSwitchSide(context, 99L, any(), any(), any()) }
    }

    @Test
    fun switchSideTypePassesElapsedMinutesFromIntent() = runTest {
        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = 1L,
                currentSide = "LEFT",
                elapsedMinutes = 15,
                maxTotalMinutes = 0,
            ),
        )

        verify { NotificationHelper.showSwitchSide(context, any(), any(), 15, any()) }
    }

    @Test
    fun switchSideTypeMissingCurrentSideDefaultsToLeft() = runTest {
        val intentWithNullSide =
            mockk<Intent>(relaxed = true).also {
                every { it.getStringExtra("notification_type") } returns
                    BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE
                every { it.getLongExtra("session_id", -1L) } returns 1L
                every { it.getStringExtra("current_side") } returns null
                every { it.getIntExtra("elapsed_minutes", 0) } returns 0
                every { it.getIntExtra("max_total_minutes", 0) } returns 0
            }

        receiver.handle(context, intentWithNullSide)

        verify { NotificationHelper.showSwitchSide(context, any(), "LEFT", any(), any()) }
    }

    @Test
    fun maxTotalTypeRichDisabledShowsFeedingLimitNotification() = runTest {
        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
                sessionId = 2L,
                currentSide = "LEFT",
                elapsedMinutes = 0,
                maxTotalMinutes = 30,
            ),
        )

        verify { NotificationHelper.showFeedingLimit(context, 2L, 30, false) }
    }

    @Test
    fun maxTotalTypeRichEnabledPassesRichFlagTrue() = runTest {
        every { settingsRepository.getRichNotificationsEnabled() } returns flowOf(true)

        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
                sessionId = 2L,
                currentSide = "LEFT",
                elapsedMinutes = 0,
                maxTotalMinutes = 30,
            ),
        )

        verify { NotificationHelper.showFeedingLimit(context, any(), any(), true) }
    }

    @Test
    fun maxTotalTypePassesMaxTotalMinutesFromIntent() = runTest {
        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL,
                sessionId = 1L,
                currentSide = "LEFT",
                elapsedMinutes = 0,
                maxTotalMinutes = 45,
            ),
        )

        verify { NotificationHelper.showFeedingLimit(context, any(), 45, any()) }
    }

    @Test
    fun nullNotificationTypeDoesNotCallAnyNotification() = runTest {
        val nullTypeIntent =
            mockk<Intent>(relaxed = true).also {
                every { it.getStringExtra("notification_type") } returns null
            }

        receiver.handle(context, nullTypeIntent)

        verify(exactly = 0) { NotificationHelper.showSwitchSide(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { NotificationHelper.showFeedingLimit(any(), any(), any(), any()) }
    }

    @Test
    fun unknownNotificationTypeDoesNotCallAnyNotification() = runTest {
        receiver.handle(
            context,
            intent(
                type = "completely_unknown",
                sessionId = 1L,
                currentSide = "LEFT",
                elapsedMinutes = 0,
                maxTotalMinutes = 0,
            ),
        )

        verify(exactly = 0) { NotificationHelper.showSwitchSide(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { NotificationHelper.showFeedingLimit(any(), any(), any(), any()) }
    }

    @Test
    fun switchSideTypeDoesNotCallFeedingLimit() = runTest {
        receiver.handle(
            context,
            intent(
                type = BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE,
                sessionId = 1L,
                currentSide = "LEFT",
                elapsedMinutes = 0,
                maxTotalMinutes = 0,
            ),
        )

        verify(exactly = 0) { NotificationHelper.showFeedingLimit(any(), any(), any(), any()) }
        verify(exactly = 1) { NotificationHelper.showSwitchSide(any(), any(), any(), any(), any()) }
    }

    private fun intent(
        type: String,
        sessionId: Long,
        currentSide: String,
        elapsedMinutes: Int,
        maxTotalMinutes: Int,
    ) = mockk<Intent>(relaxed = true).also {
        every { it.getStringExtra("notification_type") } returns type
        every { it.getLongExtra("session_id", -1L) } returns sessionId
        every { it.getStringExtra("current_side") } returns currentSide
        every { it.getIntExtra("elapsed_minutes", 0) } returns elapsedMinutes
        every { it.getIntExtra("max_total_minutes", 0) } returns maxTotalMinutes
    }
}
