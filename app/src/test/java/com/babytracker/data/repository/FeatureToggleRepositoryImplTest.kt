package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.AppFeature
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

class FeatureToggleRepositoryImplTest {

    private val key = stringPreferencesKey("enabled_features")
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: FeatureToggleRepositoryImpl

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = FeatureToggleRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `getEnabledFeatures returns ALL when key absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[key] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(AppFeature.ALL, repository.getEnabledFeatures().first())
    }

    @Test
    fun `getEnabledFeatures parses stored names`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[key] } returns "SLEEP,DIAPERS"
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(setOf(AppFeature.SLEEP, AppFeature.DIAPERS), repository.getEnabledFeatures().first())
    }

    @Test
    fun `setEnabledFeatures persists comma-joined names`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setEnabledFeatures(setOf(AppFeature.SLEEP))

        coVerify { dataStore.edit(any()) }
        val prefs = mutablePreferencesOf()
        editSlot.captured.invoke(prefs)
        assertEquals("SLEEP", prefs[key])
    }
}
