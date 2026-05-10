package com.babytracker.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.ThemeConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BabyTrackerThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun localDarkTheme_isTrueWhenThemeConfigIsDark() {
        var captured = false
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.DARK) {
                captured = LocalDarkTheme.current
            }
        }
        composeRule.waitForIdle()
        assertTrue(captured)
    }

    @Test
    fun localDarkTheme_isFalseWhenThemeConfigIsLight() {
        var captured = true
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                captured = LocalDarkTheme.current
            }
        }
        composeRule.waitForIdle()
        assertFalse(captured)
    }
}
