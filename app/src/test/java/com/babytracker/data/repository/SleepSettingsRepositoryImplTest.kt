package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SleepSettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SleepSettingsRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = SleepSettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `getNapReminderEnabled returns false when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("nap_reminder_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getNapReminderEnabled().first())
    }

    @Test
    fun `getNapReminderEnabled returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("nap_reminder_enabled")] } returns true
        every { dataStore.data } returns flowOf(prefs)

        assertTrue(repository.getNapReminderEnabled().first())
    }

    @Test
    fun `setNapReminderEnabled persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setNapReminderEnabled(true)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertTrue(prefs[booleanPreferencesKey("nap_reminder_enabled")]!!)
    }

    @Test
    fun `getNapReminderDelayMinutes returns 60 when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("nap_reminder_delay_minutes")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(60, repository.getNapReminderDelayMinutes().first())
    }

    @Test
    fun `setNapReminderDelayMinutes clamps value below 1 to 1`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setNapReminderDelayMinutes(0)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(1, prefs[intPreferencesKey("nap_reminder_delay_minutes")])
    }

    @Test
    fun `setNapReminderDelayMinutes clamps value above 480 to 480`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setNapReminderDelayMinutes(999)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(480, prefs[intPreferencesKey("nap_reminder_delay_minutes")])
    }

    @Test
    fun `setNapReminderDelayMinutes accepts value within range`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setNapReminderDelayMinutes(90)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(90, prefs[intPreferencesKey("nap_reminder_delay_minutes")])
    }

    @Test
    fun `getPredictiveSleepEnabled returns false when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("predictive_sleep_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getPredictiveSleepEnabled().first())
    }

    @Test
    fun `getPredictiveSleepEnabled returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("predictive_sleep_enabled")] } returns true
        every { dataStore.data } returns flowOf(prefs)

        assertTrue(repository.getPredictiveSleepEnabled().first())
    }

    @Test
    fun `setPredictiveSleepEnabled persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveSleepEnabled(true)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertTrue(prefs[booleanPreferencesKey("predictive_sleep_enabled")]!!)
    }

    @Test
    fun `getPredictiveSleepLeadMinutes returns 15 when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("predictive_sleep_lead_minutes")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(15, repository.getPredictiveSleepLeadMinutes().first())
    }

    @Test
    fun `getPredictiveSleepLeadMinutes returns default when stored value is not allowed`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("predictive_sleep_lead_minutes")] } returns 7
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(15, repository.getPredictiveSleepLeadMinutes().first())
    }

    @Test
    fun `getPredictiveSleepLeadMinutes returns stored allowed value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("predictive_sleep_lead_minutes")] } returns 30
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(30, repository.getPredictiveSleepLeadMinutes().first())
    }

    @Test
    fun `setPredictiveSleepLeadMinutes sanitizes disallowed value to default`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveSleepLeadMinutes(99)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(15, prefs[intPreferencesKey("predictive_sleep_lead_minutes")])
    }

    @Test
    fun `setPredictiveSleepLeadMinutes accepts allowed value`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveSleepLeadMinutes(10)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(10, prefs[intPreferencesKey("predictive_sleep_lead_minutes")])
    }
}
