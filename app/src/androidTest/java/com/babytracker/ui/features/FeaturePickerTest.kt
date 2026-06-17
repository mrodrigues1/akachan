package com.babytracker.ui.features

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureSelection
import org.junit.Rule
import org.junit.Test

class FeaturePickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun togglingSleepDomain_flipsItsSwitch() {
        composeRule.setContent {
            var enabled by mutableStateOf(AppFeature.ALL)
            FeaturePicker(
                enabledFeatures = enabled,
                onFeatureToggled = { f, on -> enabled = FeatureSelection.setFeature(enabled, f, on) },
                onDomainToggled = { d, on -> enabled = FeatureSelection.setDomain(enabled, d, on) },
            )
        }

        composeRule.onNodeWithTag("feature_domain_switch_SLEEP").assertIsOn()
        composeRule.onNodeWithTag("feature_domain_switch_SLEEP").performClick()
        composeRule.onNodeWithTag("feature_domain_switch_SLEEP").assertIsOff()
    }

    @Test
    fun expandingFeedingDomain_revealsPerTrackerSwitches() {
        composeRule.setContent {
            var enabled by mutableStateOf(AppFeature.ALL)
            FeaturePicker(
                enabledFeatures = enabled,
                onFeatureToggled = { f, on -> enabled = FeatureSelection.setFeature(enabled, f, on) },
                onDomainToggled = { d, on -> enabled = FeatureSelection.setDomain(enabled, d, on) },
            )
        }

        composeRule.onNodeWithContentDescription("Customize Feeding trackers").performClick()
        composeRule.onNodeWithTag("feature_switch_PUMPING").assertIsOn()
        composeRule.onNodeWithTag("feature_switch_PUMPING").performClick()
        composeRule.onNodeWithTag("feature_switch_PUMPING").assertIsOff()
    }
}
