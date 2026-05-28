package com.babytracker.widget

import com.babytracker.domain.model.BreastSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetContentHelpersTest {

    @Test
    fun `label maps LEFT to "Left"`() {
        assertEquals("Left", BreastSide.LEFT.label())
    }

    @Test
    fun `label maps RIGHT to "Right"`() {
        assertEquals("Right", BreastSide.RIGHT.label())
    }
}
