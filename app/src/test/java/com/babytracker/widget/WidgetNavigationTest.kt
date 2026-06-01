package com.babytracker.widget

import androidx.glance.appwidget.action.RunCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetNavigationTest {

    @Test
    fun `refreshAction runs the widget refresh callback`() {
        val action = refreshAction()

        assertTrue(action is RunCallbackAction)
        assertEquals(WidgetRefreshActionCallback::class.java, (action as RunCallbackAction).callbackClass)
    }
}

