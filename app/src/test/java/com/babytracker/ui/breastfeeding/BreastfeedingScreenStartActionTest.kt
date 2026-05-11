package com.babytracker.ui.breastfeeding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BreastfeedingScreenStartActionTest {

    @Test
    fun `start action does nothing when no side is selected`() {
        val action = resolveStartSessionAction(
            hasSelectedSide = false,
            shouldRequestNotificationPermission = true,
            notificationPermissionGranted = false
        )

        assertEquals(StartSessionAction.NoOp, action)
    }

    @Test
    fun `start action starts immediately when notification permission is already granted`() {
        val action = resolveStartSessionAction(
            hasSelectedSide = true,
            shouldRequestNotificationPermission = true,
            notificationPermissionGranted = true
        )

        assertEquals(StartSessionAction.StartOnly, action)
    }

    @Test
    fun `start action requests notification permission and still starts session`() {
        val action = resolveStartSessionAction(
            hasSelectedSide = true,
            shouldRequestNotificationPermission = true,
            notificationPermissionGranted = false
        )

        assertEquals(StartSessionAction.RequestPermissionAndStart, action)
    }
}
