package com.babytracker.ui.features

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.babytracker.domain.model.AppFeature
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test

class FeaturesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_showsTitleAndPicker() {
        composeRule.setContent {
            BabyTrackerTheme {
                FeaturesContent(
                    enabledFeatures = AppFeature.ALL,
                    onFeatureToggled = { _, _ -> },
                    onDomainToggled = { _, _ -> },
                    onNavigateBack = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }

        composeRule.onNodeWithText("What you track").assertIsDisplayed()
        composeRule.onNodeWithTag("feature_domain_switch_SLEEP").assertIsDisplayed()
    }
}
