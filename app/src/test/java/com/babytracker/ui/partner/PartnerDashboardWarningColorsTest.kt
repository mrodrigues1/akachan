package com.babytracker.ui.partner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.SurfaceDark
import com.babytracker.ui.theme.SurfaceYellow
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

class PartnerDashboardWarningColorsTest {

    @Test
    fun `light warning colors use light warning tokens`() {
        val colors = partnerWarningColors(isDark = false)

        assertEquals(WarningAmber, colors.accent)
        assertEquals(WarningContainerAmber, colors.container)
        assertEquals(OnWarningContainerAmber, colors.onContainer)
        assertEquals(OnWarningContainerAmber, colors.onSurfaceAccent)
    }

    @Test
    fun `dark warning colors use dark warning tokens`() {
        val colors = partnerWarningColors(isDark = true)

        assertEquals(WarningAmberDark, colors.accent)
        assertEquals(WarningContainerAmberDark, colors.container)
        assertEquals(OnWarningContainerAmberDark, colors.onContainer)
        assertEquals(OnWarningContainerAmberDark, colors.onSurfaceAccent)
    }

    @Test
    fun `warning text colors meet contrast on screen surfaces`() {
        val lightColors = partnerWarningColors(isDark = false)
        val darkColors = partnerWarningColors(isDark = true)

        assertContrastAtLeast(lightColors.onSurfaceAccent, SurfaceYellow)
        assertContrastAtLeast(darkColors.onSurfaceAccent, SurfaceDark)
        assertContrastAtLeast(lightColors.onContainer, lightColors.container)
        assertContrastAtLeast(darkColors.onContainer, darkColors.container)
    }

    private fun assertContrastAtLeast(
        foreground: Color,
        background: Color,
        minimumRatio: Float = 4.5f,
    ) {
        val ratio = contrastRatio(foreground, background)

        assertTrue(ratio >= minimumRatio, "Expected contrast $ratio to be at least $minimumRatio")
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()

        return (max(foregroundLuminance, backgroundLuminance) + CONTRAST_OFFSET) /
            (min(foregroundLuminance, backgroundLuminance) + CONTRAST_OFFSET)
    }

    private companion object {
        const val CONTRAST_OFFSET = 0.05f
    }
}
