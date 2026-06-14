package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BabyRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: BabyRepositoryImpl

    private val nameKey = stringPreferencesKey("baby_name")
    private val birthKey = longPreferencesKey("baby_birth_date")
    private val allergiesKey = stringPreferencesKey("allergies")
    private val customNoteKey = stringPreferencesKey("custom_allergy_note")
    private val sexKey = stringPreferencesKey("baby_sex")

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = BabyRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun prefsWith(sexValue: String?): Preferences {
        val prefs = mockk<Preferences>()
        every { prefs[nameKey] } returns "Leo"
        every { prefs[birthKey] } returns LocalDate.of(2026, 1, 1).toEpochDay()
        every { prefs[allergiesKey] } returns null
        every { prefs[customNoteKey] } returns null
        every { prefs[sexKey] } returns sexValue
        return prefs
    }

    @Test
    fun `getBabyProfile defaults sex to UNSPECIFIED when key absent`() = runTest {
        every { dataStore.data } returns flowOf(prefsWith(sexValue = null))

        assertEquals(BabySex.UNSPECIFIED, repository.getBabyProfile().first()?.sex)
    }

    @Test
    fun `getBabyProfile reads stored sex`() = runTest {
        every { dataStore.data } returns flowOf(prefsWith(sexValue = "FEMALE"))

        assertEquals(BabySex.FEMALE, repository.getBabyProfile().first()?.sex)
    }

    @Test
    fun `getBabyProfile falls back to UNSPECIFIED for unrecognised sex`() = runTest {
        every { dataStore.data } returns flowOf(prefsWith(sexValue = "NONBINARY_FUTURE_VALUE"))

        assertEquals(BabySex.UNSPECIFIED, repository.getBabyProfile().first()?.sex)
    }

    @Test
    fun `saveBabyProfile persists sex name`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.saveBabyProfile(
            Baby(name = "Leo", birthDate = LocalDate.of(2026, 1, 1), sex = BabySex.MALE),
        )

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals("MALE", prefs[sexKey])
    }
}
