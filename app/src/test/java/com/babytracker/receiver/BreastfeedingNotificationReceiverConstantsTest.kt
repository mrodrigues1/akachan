package com.babytracker.receiver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreastfeedingNotificationReceiverConstantsTest {

    @Test
    fun `notification type constants are correctly defined`() {
        assertEquals("max_total", BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_MAX_TOTAL)
        assertEquals("switch_side", BreastfeedingNotificationReceiver.NOTIFICATION_TYPE_SWITCH_SIDE)
    }

    @Test
    fun `action string constants are unique and non-empty`() {
        val breastfeedingAction = BreastfeedingActionReceiver.ACTION
        val sleepAction = SleepActionReceiver.ACTION

        assertTrue(breastfeedingAction.isNotEmpty())
        assertTrue(sleepAction.isNotEmpty())
        assertNotEquals(breastfeedingAction, sleepAction)
    }
}
