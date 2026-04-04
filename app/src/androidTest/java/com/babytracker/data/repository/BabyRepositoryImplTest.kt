package com.babytracker.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BabyRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var prefsFile: File
    private lateinit var repository: BabyRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefsFile = File(context.filesDir, "test_baby_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { prefsFile },
        )
        repository = BabyRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        prefsFile.delete()
    }

    @Test
    fun saveThenGet_roundTrips() = runTest {
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
    fun isOnboardingComplete_afterSave_true() = runTest {
        val baby = Baby("Luna", LocalDate.now())
        repository.saveBabyProfile(baby)

        val result = repository.isOnboardingComplete().first()

        assertTrue(result)
    }

    @Test
    fun isOnboardingComplete_beforeSave_false() = runTest {
        val result = repository.isOnboardingComplete().first()
        assertFalse(result)
    }

    @Test
    fun saveTwice_overwritesPrevious() = runTest {
        val babyA = Baby("Luna", LocalDate.of(2025, 1, 1))
        val babyB = Baby("Stella", LocalDate.of(2025, 6, 1))

        repository.saveBabyProfile(babyA)
        repository.saveBabyProfile(babyB)

        val result = repository.getBabyProfile().first()
        assertEquals("Stella", result?.name)
        assertEquals(LocalDate.of(2025, 6, 1), result?.birthDate)
    }

    @Test
    fun getAllergies_multipleSelected_roundTrips() = runTest {
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
