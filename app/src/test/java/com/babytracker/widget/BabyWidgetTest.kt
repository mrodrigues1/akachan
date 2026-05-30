package com.babytracker.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BabyWidgetTest {

    @Test
    fun `responsive sizes include declared minimum widget bounds`() {
        assertEquals(DpSize(width = 110.dp, height = 64.dp), BabyWidget.COMPACT_NARROW_SIZE)
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.COMPACT_NARROW_SIZE))
    }

    @Test
    fun `uses compact wide layout for wide one-row size`() {
        val size = DpSize(width = 180.dp, height = 64.dp)

        assertEquals(WidgetLayout.COMPACT_WIDE, widgetLayoutForSize(size))
    }

    @Test
    fun `uses compact narrow layout for legal narrow one-row size`() {
        val size = DpSize(width = 110.dp, height = 64.dp)

        assertEquals(WidgetLayout.COMPACT_NARROW, widgetLayoutForSize(size))
    }

    @Test
    fun `keeps square and tall sizes on medium layout`() {
        assertEquals(WidgetLayout.MEDIUM, widgetLayoutForSize(DpSize(width = 110.dp, height = 110.dp)))
        assertEquals(WidgetLayout.MEDIUM, widgetLayoutForSize(DpSize(width = 180.dp, height = 110.dp)))
    }
}
