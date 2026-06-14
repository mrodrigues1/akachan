package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Quiet hours remain on [SettingsRepository] because they are shared between predictive feeding
 * and predictive sleep notifications. (Feeding limits + predictive feeding prefs moved to
 * FeedSettingsRepository — see FeedSettingsRepositoryImplTest.)
 */
class SettingsRepositoryQuietHoursTest {

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
