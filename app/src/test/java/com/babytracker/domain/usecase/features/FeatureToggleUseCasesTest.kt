package com.babytracker.domain.usecase.features

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.repository.FeatureToggleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeatureToggleUseCasesTest {

    private lateinit var repository: FeatureToggleRepository

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
    }

    @Test
    fun `set feature enabled persists the new set and returns true`() = runTest {
        coEvery { repository.getEnabledFeatures() } returns flowOf(setOf(AppFeature.SLEEP))

        val applied = SetFeatureEnabledUseCase(repository)(AppFeature.GROWTH, enabled = true)

        assertTrue(applied)
        coVerify { repository.setEnabledFeatures(setOf(AppFeature.SLEEP, AppFeature.GROWTH)) }
    }

    @Test
    fun `disabling the last feature is rejected and nothing is persisted`() = runTest {
        coEvery { repository.getEnabledFeatures() } returns flowOf(setOf(AppFeature.SLEEP))

        val applied = SetFeatureEnabledUseCase(repository)(AppFeature.SLEEP, enabled = false)

        assertFalse(applied)
        coVerify(exactly = 0) { repository.setEnabledFeatures(any()) }
    }

    @Test
    fun `set domain enabled persists all its features`() = runTest {
        coEvery { repository.getEnabledFeatures() } returns flowOf(setOf(AppFeature.SLEEP))

        val applied = SetDomainEnabledUseCase(repository)(FeatureDomain.FEEDING, enabled = true)

        assertTrue(applied)
        coVerify { repository.setEnabledFeatures(setOf(AppFeature.SLEEP) + FeatureDomain.FEEDING.features) }
    }
}
