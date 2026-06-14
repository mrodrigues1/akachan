package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.MeasurementSystem
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

class SettingsMeasurementSystemTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl
    private val key = stringPreferencesKey("measurement_system")

    @BeforeEach
    fun setup() {
        dataStore = mockk()
        repository = SettingsRepositoryImpl(dataStore)
        mockkStatic("androidx.datastore.preferences.core.PreferencesKt")
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `getMeasurementSystem defaults to METRIC when absent`() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[key] } returns null
        every { dataStore.data } returns flowOf(prefs)

        assertEquals(MeasurementSystem.METRIC, repository.getMeasurementSystem().first())
    }

    @Test
    fun `getMeasurementSystem reads stored value and tolerates garbage`() = runTest {
        val imperial = mockk<Preferences>()
        every { imperial[key] } returns "IMPERIAL"
        val garbage = mockk<Preferences>()
        every { garbage[key] } returns "NOT_A_SYSTEM"

        every { dataStore.data } returns flowOf(imperial)
        assertEquals(MeasurementSystem.IMPERIAL, repository.getMeasurementSystem().first())

        every { dataStore.data } returns flowOf(garbage)
        assertEquals(MeasurementSystem.METRIC, repository.getMeasurementSystem().first())
    }

    @Test
    fun `setMeasurementSystem persists the enum name`() = runTest {
        val editSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mockk()

        repository.setMeasurementSystem(MeasurementSystem.IMPERIAL)

        val prefs = mutablePreferencesOf()
        editSlot.captured(prefs)
        assertEquals("IMPERIAL", prefs[key])
    }
}
