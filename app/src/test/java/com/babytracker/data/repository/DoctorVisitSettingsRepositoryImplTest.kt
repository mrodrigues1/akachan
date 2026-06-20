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

class DoctorVisitSettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DoctorVisitSettingsRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = DoctorVisitSettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `getReminderEnabled defaults to false`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("doctor_visit_reminder_enabled")] } returns null
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
        assertTrue(prefs[booleanPreferencesKey("doctor_visit_reminder_enabled")]!!)
    }

    @Test
    fun `getReminderLeadDays defaults to 1`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("doctor_visit_reminder_lead_days")] } returns null
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(1, repository.getReminderLeadDays().first())
    }

    @Test
    fun `getReminderLeadDays returns default when stored value is disallowed`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("doctor_visit_reminder_lead_days")] } returns 5
        every { dataStore.data } returns flowOf(prefs)
        assertEquals(1, repository.getReminderLeadDays().first())
    }

    @Test
    fun `getReminderLeadDays returns stored allowed value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("doctor_visit_reminder_lead_days")] } returns 14
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
        assertEquals(1, prefs[intPreferencesKey("doctor_visit_reminder_lead_days")])
    }

    @Test
    fun `setReminderLeadDays accepts allowed value`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()
        repository.setReminderLeadDays(3)
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(3, prefs[intPreferencesKey("doctor_visit_reminder_lead_days")])
    }
}
