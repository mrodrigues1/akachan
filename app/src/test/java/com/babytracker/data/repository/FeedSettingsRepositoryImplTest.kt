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

class FeedSettingsRepositoryImplTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: FeedSettingsRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = FeedSettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `getMaxPerBreastMinutes returns 0 when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("max_per_breast_minutes")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(0, repository.getMaxPerBreastMinutes().first())
    }

    @Test
    fun `getMaxPerBreastMinutes returns stored value (reused key, no migration)`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("max_per_breast_minutes")] } returns 12
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(12, repository.getMaxPerBreastMinutes().first())
    }

    @Test
    fun `setMaxPerBreastMinutes persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setMaxPerBreastMinutes(20)

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(20, prefs[intPreferencesKey("max_per_breast_minutes")])
    }

    @Test
    fun `getMaxTotalFeedMinutes returns 0 when key is absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("max_total_feed_minutes")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(0, repository.getMaxTotalFeedMinutes().first())
    }

    @Test
    fun `setMaxTotalFeedMinutes persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setMaxTotalFeedMinutes(45)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(45, prefs[intPreferencesKey("max_total_feed_minutes")])
    }

    @Test
    fun `getPredictiveEnabled defaults to false`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[booleanPreferencesKey("predictive_enabled")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertFalse(repository.getPredictiveEnabled().first())
    }

    @Test
    fun `setPredictiveEnabled persists value to DataStore`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveEnabled(true)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertTrue(prefs[booleanPreferencesKey("predictive_enabled")]!!)
    }

    @Test
    fun `getPredictiveLeadMinutes defaults to 15`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(15, repository.getPredictiveLeadMinutes().first())
    }

    @Test
    fun `getPredictiveLeadMinutes returns stored allowed value`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[intPreferencesKey("predictive_lead_minutes")] } returns 30
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(30, repository.getPredictiveLeadMinutes().first())
    }

    @Test
    fun `getPredictiveLeadMinutes falls back to 15 when stored value is not in the allowlist`() = runTest {
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
    fun `setPredictiveLeadMinutes persists an allowed value`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveLeadMinutes(30)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(30, prefs[intPreferencesKey("predictive_lead_minutes")])
    }

    @Test
    fun `setPredictiveLeadMinutes sanitizes disallowed value to default`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setPredictiveLeadMinutes(99)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals(15, prefs[intPreferencesKey("predictive_lead_minutes")])
    }
}
