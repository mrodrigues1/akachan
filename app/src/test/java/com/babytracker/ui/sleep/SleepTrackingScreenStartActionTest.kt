package com.babytracker.ui.sleep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SleepTrackingScreenStartActionTest {

    @Test
    fun `start action starts immediately when notification permission is already granted`() {
        val action = resolveStartSleepAction(
            shouldRequestNotificationPermission = true,
            notificationPermissionGranted = true
        )

        assertEquals(StartSleepAction.StartOnly, action)
    }

    @Test
    fun `start action requests notification permission and still starts session`() {
        val action = resolveStartSleepAction(
            shouldRequestNotificationPermission = true,
            notificationPermissionGranted = false
        )

        assertEquals(StartSleepAction.RequestPermissionAndStart, action)
    }

    @Test
    fun `start action skips permission request below API 33`() {
        val action = resolveStartSleepAction(
            shouldRequestNotificationPermission = false,
            notificationPermissionGranted = false
        )

        assertEquals(StartSleepAction.StartOnly, action)
    }
}
