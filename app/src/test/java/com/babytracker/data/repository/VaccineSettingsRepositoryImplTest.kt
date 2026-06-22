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

class VaccineSettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: VaccineSettingsRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = VaccineSettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `getReminderEnabled defaults to false`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("vaccine_reminder_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)
        assertFalse(repository.getReminderEnabled().first())
    }

    @Test
    fun `setReminderEnabled persists value`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()
        repository.setReminderEnabled(true)
        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertTrue(prefs[booleanPreferencesKey("vaccine_reminder_enabled")]!!)
    }

    @Test
    fun `getReminderLeadDays defaults to 7`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("vaccine_reminder_lead_days")] } returns null
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(7, repository.getReminderLeadDays().first())
    }

    @Test
    fun `getReminderLeadDays returns default when stored value is disallowed`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("vaccine_reminder_lead_days")] } returns 5
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(7, repository.getReminderLeadDays().first())
    }

    @Test
    fun `getReminderLeadDays returns stored allowed value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("vaccine_reminder_lead_days")] } returns 14
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(14, repository.getReminderLeadDays().first())
    }

    @Test
    fun `setReminderLeadDays sanitizes disallowed value to default`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()
        repository.setReminderLeadDays(99)
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(7, prefs[intPreferencesKey("vaccine_reminder_lead_days")])
    }

    @Test
    fun `setReminderLeadDays accepts allowed value`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()
        repository.setReminderLeadDays(3)
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(3, prefs[intPreferencesKey("vaccine_reminder_lead_days")])
    }

    @Test
    fun `getToScheduleLeadDays defaults to 14`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("vaccine_to_schedule_lead_days")] } returns null
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(14, repository.getToScheduleLeadDays().first())
    }

    @Test
    fun `getToScheduleLeadDays returns default when stored value is disallowed`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("vaccine_to_schedule_lead_days")] } returns 5
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(14, repository.getToScheduleLeadDays().first())
    }

    @Test
    fun `getToScheduleLeadDays returns stored allowed value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("vaccine_to_schedule_lead_days")] } returns 30
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(30, repository.getToScheduleLeadDays().first())
    }

    @Test
    fun `setToScheduleLeadDays sanitizes disallowed value to default`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()
        repository.setToScheduleLeadDays(5)
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(14, prefs[intPreferencesKey("vaccine_to_schedule_lead_days")])
    }

    @Test
    fun `setToScheduleLeadDays accepts allowed value`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()
        repository.setToScheduleLeadDays(30)
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(30, prefs[intPreferencesKey("vaccine_to_schedule_lead_days")])
    }
}
