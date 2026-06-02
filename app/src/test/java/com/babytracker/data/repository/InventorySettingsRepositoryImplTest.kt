package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InventorySettingsRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: InventorySettingsRepository

    @BeforeEach
    fun setup() {
        scope = CoroutineScope(Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "test.preferences_pb")
        }
        repo = InventorySettingsRepositoryImpl(dataStore)
    }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `defaults when nothing stored`() = runTest {
        assertEquals(false, repo.getExpirationEnabled().first())
        assertEquals(4, repo.getExpirationDays().first())
        assertEquals(false, repo.getExpirationNotifEnabled().first())
        assertEquals(480, repo.getExpirationNotifTimeMinutes().first())
    }

    @Test
    fun `setExpirationDays coerces zero to one`() = runTest {
        repo.setExpirationDays(0)
        assertEquals(1, repo.getExpirationDays().first())
    }

    @Test
    fun `setExpirationDays coerces negative to one`() = runTest {
        repo.setExpirationDays(-5)
        assertEquals(1, repo.getExpirationDays().first())
    }

    @Test
    fun `notif time coerces out-of-range on write`() = runTest {
        repo.setExpirationNotifTimeMinutes(5000)
        assertEquals(1439, repo.getExpirationNotifTimeMinutes().first())
        repo.setExpirationNotifTimeMinutes(-10)
        assertEquals(0, repo.getExpirationNotifTimeMinutes().first())
    }

    @Test
    fun `round-trips each setting`() = runTest {
        repo.setExpirationEnabled(true)
        repo.setExpirationDays(7)
        repo.setExpirationNotifEnabled(true)
        repo.setExpirationNotifTimeMinutes(600)

        assertEquals(true, repo.getExpirationEnabled().first())
        assertEquals(7, repo.getExpirationDays().first())
        assertEquals(true, repo.getExpirationNotifEnabled().first())
        assertEquals(600, repo.getExpirationNotifTimeMinutes().first())
    }
}
