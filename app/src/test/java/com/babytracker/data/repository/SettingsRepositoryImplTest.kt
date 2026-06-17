package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.VolumeUnit
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
    fun `getPartnerFeedStashNotificationsEnabled returns true when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every {
            prefs[booleanPreferencesKey("partner_feed_stash_notifications_enabled")]
        } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertTrue(repository.getPartnerFeedStashNotificationsEnabled().first())
    }

    @Test
    fun `getPartnerFeedStashNotificationsEnabled returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every {
            prefs[booleanPreferencesKey("partner_feed_stash_notifications_enabled")]
        } returns false
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getPartnerFeedStashNotificationsEnabled().first())
    }

    @Test
    fun `setPartnerFeedStashNotificationsEnabled persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPartnerFeedStashNotificationsEnabled(false)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertFalse(prefs[booleanPreferencesKey("partner_feed_stash_notifications_enabled")]!!)
    }

    @Test
    fun `getVolumeUnit returns ML when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[stringPreferencesKey("volume_unit")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(VolumeUnit.ML, repository.getVolumeUnit().first())
    }

    @Test
    fun `getVolumeUnit returns ML when stored value is invalid`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[stringPreferencesKey("volume_unit")] } returns "GALLONS"
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(VolumeUnit.ML, repository.getVolumeUnit().first())
    }

    @Test
    fun `getVolumeUnit returns stored value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[stringPreferencesKey("volume_unit")] } returns "OZ"
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(VolumeUnit.OZ, repository.getVolumeUnit().first())
    }

    @Test
    fun `setVolumeUnit persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setVolumeUnit(VolumeUnit.OZ)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals("OZ", prefs[stringPreferencesKey("volume_unit")])
    }
}
