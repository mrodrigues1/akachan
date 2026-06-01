package com.babytracker.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BabyWidgetTest {

    @Test
    fun `responsive sizes include declared minimum widget bounds`() {
        assertEquals(DpSize(width = 110.dp, height = 64.dp), BabyWidget.COMPACT_NARROW_SIZE)
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.COMPACT_NARROW_SIZE))
    }

    @Test
    fun `responsive sizes include medium and large widget targets`() {
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.FOUR_BY_ONE_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.MEDIUM_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.THREE_BY_THREE_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.TWO_BY_FOUR_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.THREE_BY_FOUR_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.FOUR_BY_TWO_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.FOUR_BY_THREE_SIZE))
        assertTrue(BabyWidget.RESPONSIVE_SIZES.contains(BabyWidget.FOUR_BY_FOUR_SIZE))
    }

    @Test
    fun `uses compact wide layout for wide one-row size`() {
        val size = DpSize(width = 180.dp, height = 64.dp)

        assertEquals(WidgetLayout.COMPACT_WIDE, widgetLayoutForSize(size))
    }

    @Test
    fun `keeps four by one layout compact wide`() {
        assertEquals(WidgetLayout.COMPACT_WIDE, widgetLayoutForSize(BabyWidget.FOUR_BY_ONE_SIZE))
    }

    @Test
    fun `uses compact narrow layout for legal narrow one-row size`() {
        val size = DpSize(width = 110.dp, height = 64.dp)

        assertEquals(WidgetLayout.COMPACT_NARROW, widgetLayoutForSize(size))
    }

    @Test
    fun `keeps square and short-but-wide sizes on medium layout`() {
        assertEquals(WidgetLayout.MEDIUM, widgetLayoutForSize(DpSize(width = 110.dp, height = 110.dp)))
        assertEquals(WidgetLayout.MEDIUM, widgetLayoutForSize(DpSize(width = 180.dp, height = 110.dp)))
    }

    @Test
    fun `uses explicit layout for two by four size`() {
        assertEquals(WidgetLayout.TWO_BY_FOUR, widgetLayoutForSize(BabyWidget.TWO_BY_FOUR_SIZE))
    }

    @Test
    fun `uses explicit layout for three by three size`() {
        assertEquals(WidgetLayout.THREE_BY_THREE, widgetLayoutForSize(BabyWidget.THREE_BY_THREE_SIZE))
    }

    @Test
    fun `uses explicit layout for three by four size`() {
        assertEquals(WidgetLayout.THREE_BY_FOUR, widgetLayoutForSize(BabyWidget.THREE_BY_FOUR_SIZE))
    }

    @Test
    fun `uses explicit layout for four by two size`() {
        assertEquals(WidgetLayout.FOUR_BY_TWO, widgetLayoutForSize(BabyWidget.FOUR_BY_TWO_SIZE))
    }

    @Test
    fun `uses explicit layout for four by three size`() {
        assertEquals(WidgetLayout.FOUR_BY_THREE, widgetLayoutForSize(BabyWidget.FOUR_BY_THREE_SIZE))
    }

    @Test
    fun `uses explicit layout for four by four size`() {
        assertEquals(WidgetLayout.FOUR_BY_FOUR, widgetLayoutForSize(BabyWidget.FOUR_BY_FOUR_SIZE))
    }

    @Test
    fun `compact layouts do not support refresh button`() {
        assertFalse(WidgetLayout.COMPACT_NARROW.supportsRefreshButton)
        assertFalse(WidgetLayout.COMPACT_WIDE.supportsRefreshButton)
    }

    @Test
    fun `all non-compact layouts support refresh button`() {
        val nonCompact = listOf(
            WidgetLayout.MEDIUM,
            WidgetLayout.TWO_BY_FOUR,
            WidgetLayout.THREE_BY_THREE,
            WidgetLayout.THREE_BY_FOUR,
            WidgetLayout.FOUR_BY_TWO,
            WidgetLayout.FOUR_BY_THREE,
            WidgetLayout.FOUR_BY_FOUR,
        )
        nonCompact.forEach { assertTrue(it.supportsRefreshButton) }
    }

    @Test
    fun `compact sizes resolve to layouts that do not support refresh button`() {
        assertFalse(widgetLayoutForSize(BabyWidget.COMPACT_NARROW_SIZE).supportsRefreshButton)
        assertFalse(widgetLayoutForSize(BabyWidget.COMPACT_WIDE_SIZE).supportsRefreshButton)
        assertFalse(widgetLayoutForSize(BabyWidget.FOUR_BY_ONE_SIZE).supportsRefreshButton)
    }

    @Test
    fun `eligible sizes resolve to layouts that support refresh button`() {
        listOf(
            BabyWidget.MEDIUM_SIZE,
            BabyWidget.TWO_BY_FOUR_SIZE,
            BabyWidget.THREE_BY_THREE_SIZE,
            BabyWidget.THREE_BY_FOUR_SIZE,
            BabyWidget.FOUR_BY_TWO_SIZE,
            BabyWidget.FOUR_BY_THREE_SIZE,
            BabyWidget.FOUR_BY_FOUR_SIZE,
        ).forEach { size ->
            assertTrue(widgetLayoutForSize(size).supportsRefreshButton)
        }
    }
}
