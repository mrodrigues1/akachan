package com.babytracker.ui.partner

import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PartnerDashboardWarningColorsTest {

    @Test
    fun `light warning colors use light warning tokens`() {
        val colors = partnerWarningColors(isDark = false)

        assertEquals(WarningAmber, colors.accent)
        assertEquals(WarningContainerAmber, colors.container)
        assertEquals(OnWarningContainerAmber, colors.onContainer)
    }

    @Test
    fun `dark warning colors use dark warning tokens`() {
        val colors = partnerWarningColors(isDark = true)

        assertEquals(WarningAmberDark, colors.accent)
        assertEquals(WarningContainerAmberDark, colors.container)
        assertEquals(OnWarningContainerAmberDark, colors.onContainer)
    }
}
