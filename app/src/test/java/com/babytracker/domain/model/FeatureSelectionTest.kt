package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeatureSelectionTest {

    @Test
    fun `enabling a feature adds it`() {
        val result = FeatureSelection.setFeature(setOf(AppFeature.SLEEP), AppFeature.GROWTH, enabled = true)
        assertEquals(setOf(AppFeature.SLEEP, AppFeature.GROWTH), result)
    }

    @Test
    fun `disabling a feature removes it`() {
        val result = FeatureSelection.setFeature(
            setOf(AppFeature.SLEEP, AppFeature.GROWTH),
            AppFeature.GROWTH,
            enabled = false,
        )
        assertEquals(setOf(AppFeature.SLEEP), result)
    }

    @Test
    fun `disabling the last feature is rejected and returns current`() {
        val current = setOf(AppFeature.SLEEP)
        val result = FeatureSelection.setFeature(current, AppFeature.SLEEP, enabled = false)
        assertEquals(current, result)
    }

    @Test
    fun `enabling a domain adds all its features`() {
        val result = FeatureSelection.setDomain(setOf(AppFeature.SLEEP), FeatureDomain.FEEDING, enabled = true)
        assertEquals(setOf(AppFeature.SLEEP) + FeatureDomain.FEEDING.features, result)
    }

    @Test
    fun `disabling a domain removes all its features`() {
        val current = FeatureDomain.FEEDING.features.toSet() + AppFeature.SLEEP
        val result = FeatureSelection.setDomain(current, FeatureDomain.FEEDING, enabled = false)
        assertEquals(setOf(AppFeature.SLEEP), result)
    }

    @Test
    fun `disabling the only remaining domain is rejected and returns current`() {
        val current = FeatureDomain.FEEDING.features.toSet()
        val result = FeatureSelection.setDomain(current, FeatureDomain.FEEDING, enabled = false)
        assertEquals(current, result)
    }
}
