package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRepositoryPredictivePrefsTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = SettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `predictiveEnabled defaults to false`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("predictive_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getPredictiveEnabled().first())
    }

    @Test
    fun `predictiveEnabled persists`() = runTest {
        val editSlot = slot<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveEnabled(true)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertTrue(prefs[booleanPreferencesKey("predictive_enabled")]!!)
    }

    @Test
    fun `predictiveLeadMinutes defaults to 15`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(15, repository.getPredictiveLeadMinutes().first())
    }

    @Test
    fun `predictiveLeadMinutes persists an allowed value`() = runTest {
        val editSlot = slot<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveLeadMinutes(30)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(30, prefs[intPreferencesKey("predictive_lead_minutes")]!!)
    }

    @Test
    fun `predictiveLeadMinutes falls back to 15 when stored value is not in the allowlist`() = runTest {
        val prefs = mockk<Preferences>()
        every { dataStore.data } returns flowOf(prefs)

        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns 7
        assertEquals(15, repository.getPredictiveLeadMinutes().first())

        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns -5
        assertEquals(15, repository.getPredictiveLeadMinutes().first())

        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns 0
        assertEquals(15, repository.getPredictiveLeadMinutes().first())

        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns 120
        assertEquals(15, repository.getPredictiveLeadMinutes().first())
    }

    @Test
    fun `quietHoursStartMinute defaults to 0`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("quiet_hours_start_minute")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(0, repository.getQuietHoursStartMinute().first())
    }

    @Test
    fun `quietHoursEndMinute defaults to 480`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("quiet_hours_end_minute")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(480, repository.getQuietHoursEndMinute().first())
    }

    @Test
    fun `quietHoursStartMinute persists set value`() = runTest {
        val editSlot = slot<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setQuietHoursStartMinute(60)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(60, prefs[intPreferencesKey("quiet_hours_start_minute")]!!)
    }
}
