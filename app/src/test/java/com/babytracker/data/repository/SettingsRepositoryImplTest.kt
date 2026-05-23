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

class SettingsRepositoryImplTest {

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
    fun `getRichNotificationsEnabled returns true when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("rich_notifications_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertTrue(repository.getRichNotificationsEnabled().first())
    }

    @Test
    fun `getRichNotificationsEnabled returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("rich_notifications_enabled")] } returns false
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getRichNotificationsEnabled().first())
    }

    @Test
    fun `setRichNotificationsEnabled persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setRichNotificationsEnabled(false)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertFalse(prefs[booleanPreferencesKey("rich_notifications_enabled")]!!)
    }

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
}
