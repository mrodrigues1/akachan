package com.babytracker.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BabyRepositoryImplTest {

    private lateinit var testScope: TestScope
    private lateinit var prefsFile: File
    private lateinit var repository: BabyRepositoryImpl

    @BeforeEach
    fun setUp() {
        testScope = TestScope(UnconfinedTestDispatcher())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefsFile = File(context.filesDir, "test_baby_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { prefsFile },
        )
        repository = BabyRepositoryImpl(dataStore)
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
        prefsFile.delete()
    }

    @Test
    fun saveThenGetRoundTrips() = testScope.runTest {
        val baby = Baby(
            name = "Luna",
            birthDate = LocalDate.of(2025, 1, 15),
            allergies = listOf(AllergyType.CMPA, AllergyType.SOY),
            customAllergyNote = null,
        )

        repository.saveBabyProfile(baby)
        val result = repository.getBabyProfile().first()

        assertNotNull(result)
        assertEquals("Luna", result!!.name)
        assertEquals(LocalDate.of(2025, 1, 15), result.birthDate)
        assertEquals(listOf(AllergyType.CMPA, AllergyType.SOY), result.allergies)
        assertNull(result.customAllergyNote)
    }

    @Test
    fun isOnboardingCompleteAfterSaveTrue() = testScope.runTest {
        val baby = Baby("Luna", LocalDate.now())
        repository.saveBabyProfile(baby)

        val result = repository.isOnboardingComplete().first()

        assertTrue(result)
    }

    @Test
    fun isOnboardingCompleteBeforeSaveFalse() = testScope.runTest {
        val result = repository.isOnboardingComplete().first()
        assertFalse(result)
    }

    @Test
    fun saveTwiceOverwritesPrevious() = testScope.runTest {
        val babyA = Baby("Luna", LocalDate.of(2025, 1, 1))
        val babyB = Baby("Stella", LocalDate.of(2025, 6, 1))

        repository.saveBabyProfile(babyA)
        repository.saveBabyProfile(babyB)

        val result = repository.getBabyProfile().first()
        assertEquals("Stella", result?.name)
        assertEquals(LocalDate.of(2025, 6, 1), result?.birthDate)
    }

    @Test
    fun getAllergiesMultipleSelectedRoundTrips() = testScope.runTest {
        val allergies = listOf(AllergyType.CMPA, AllergyType.SOY, AllergyType.OTHER)
        val baby = Baby(
            name = "Luna",
            birthDate = LocalDate.now(),
            allergies = allergies,
            customAllergyNote = "Shellfish",
        )

        repository.saveBabyProfile(baby)
        val result = repository.getBabyProfile().first()

        assertNotNull(result)
        assertEquals(allergies, result!!.allergies)
        assertEquals("Shellfish", result.customAllergyNote)
    }
}
