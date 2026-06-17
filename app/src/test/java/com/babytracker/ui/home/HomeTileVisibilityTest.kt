package com.babytracker.ui.home

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.SleepPredictionState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeTileVisibilityTest {

    private fun state(
        enabled: Set<AppFeature> = AppFeature.ALL,
        nextSide: BreastSide? = BreastSide.LEFT,
        sleep: SleepPredictionState = SleepPredictionState.Unavailable("x"),
    ) = HomeUiState(
        enabledFeatures = enabled,
        nextRecommendedSide = nextSide,
        sleepPrediction = sleep,
    )

    @Test
    fun `breastfeeding tile hidden when feature disabled`() {
        assertFalse(HomeTile.BREASTFEEDING.isVisible(state(enabled = setOf(AppFeature.SLEEP))))
    }

    @Test
    fun `breastfeeding tile shown when feature enabled`() {
        assertTrue(HomeTile.BREASTFEEDING.isVisible(state(enabled = setOf(AppFeature.BREASTFEEDING))))
    }

    @Test
    fun `diaper tile hidden when diapers feature disabled`() {
        assertFalse(HomeTile.DIAPER.isVisible(state(enabled = setOf(AppFeature.SLEEP))))
        assertTrue(HomeTile.DIAPER.isVisible(state(enabled = setOf(AppFeature.DIAPERS))))
    }

    @Test
    fun `feeding history visible when breastfeeding OR bottle enabled`() {
        assertTrue(HomeTile.FEEDING_HISTORY.isVisible(state(enabled = setOf(AppFeature.BREASTFEEDING))))
        assertTrue(HomeTile.FEEDING_HISTORY.isVisible(state(enabled = setOf(AppFeature.BOTTLE_FEED))))
    }

    @Test
    fun `feeding history hidden when neither breastfeeding nor bottle enabled`() {
        assertFalse(HomeTile.FEEDING_HISTORY.isVisible(state(enabled = setOf(AppFeature.SLEEP))))
    }

    @Test
    fun `tip hidden when breastfeeding disabled even with a recommended side`() {
        assertFalse(HomeTile.TIP.isVisible(state(enabled = setOf(AppFeature.SLEEP), nextSide = BreastSide.LEFT)))
    }

    @Test
    fun `tip hidden when no recommended side even if breastfeeding enabled`() {
        assertFalse(HomeTile.TIP.isVisible(state(enabled = setOf(AppFeature.BREASTFEEDING), nextSide = null)))
    }

    @Test
    fun `partner tile ignores enabled features`() {
        // PARTNER visibility depends only on appMode (default NONE -> visible).
        assertTrue(HomeTile.PARTNER.isVisible(state(enabled = setOf(AppFeature.SLEEP))))
    }
}
