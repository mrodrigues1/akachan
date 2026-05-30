package com.babytracker

import com.babytracker.navigation.Routes
import com.babytracker.sharing.domain.model.AppMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PendingNavActionTest {

    private val allowed = ALLOWED_NAV_ROUTES

    @Test
    fun `returns WAIT when pending is null`() {
        assertEquals(
            PendingNavAction.WAIT,
            pendingNavAction(null, allowed, AppMode.NONE, true),
        )
    }

    @Test
    fun `returns CLEAR when route not in allowed set`() {
        assertEquals(
            PendingNavAction.CLEAR,
            pendingNavAction(Routes.SETTINGS, allowed, AppMode.NONE, true),
        )
    }

    @Test
    fun `returns WAIT when appMode is null`() {
        assertEquals(
            PendingNavAction.WAIT,
            pendingNavAction(Routes.BREASTFEEDING, allowed, null, true),
        )
    }

    @Test
    fun `returns CLEAR when appMode is PARTNER`() {
        assertEquals(
            PendingNavAction.CLEAR,
            pendingNavAction(Routes.BREASTFEEDING, allowed, AppMode.PARTNER, true),
        )
    }

    @Test
    fun `returns CLEAR when appMode is PARTNER and isOnboardingComplete is null`() {
        assertEquals(
            PendingNavAction.CLEAR,
            pendingNavAction(Routes.BREASTFEEDING, allowed, AppMode.PARTNER, null),
        )
    }

    @Test
    fun `returns WAIT when isOnboardingComplete is null`() {
        assertEquals(
            PendingNavAction.WAIT,
            pendingNavAction(Routes.BREASTFEEDING, allowed, AppMode.NONE, null),
        )
    }

    @Test
    fun `returns WAIT when isOnboardingComplete is false`() {
        assertEquals(
            PendingNavAction.WAIT,
            pendingNavAction(Routes.BREASTFEEDING, allowed, AppMode.NONE, false),
        )
    }

    @Test
    fun `returns NAVIGATE for allowed route in NONE mode when onboarding complete`() {
        assertEquals(
            PendingNavAction.NAVIGATE,
            pendingNavAction(Routes.BREASTFEEDING, allowed, AppMode.NONE, true),
        )
    }

    @Test
    fun `returns NAVIGATE for allowed route in PRIMARY mode when onboarding complete`() {
        assertEquals(
            PendingNavAction.NAVIGATE,
            pendingNavAction(Routes.SLEEP_TRACKING, allowed, AppMode.PRIMARY, true),
        )
    }
}
