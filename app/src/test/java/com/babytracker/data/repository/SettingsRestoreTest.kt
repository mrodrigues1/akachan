package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.SettingsBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "restore_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repo = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() { scope.cancel(); file.delete() }

    private fun backup(baby: BabyBackup?) = BackupData(
        backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
        baby = baby,
        settings = SettingsBackup("DARK", 12, 24, 420, false, false, true, 30, 60, 500, true, 90),
        breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
    )

    @Test
    fun `markImportInProgress sets the flag and restore clears it`() = runTest {
        repo.markImportInProgress(1234L)
        assertTrue(repo.isImportInProgress().first())

        repo.restoreFromBackup(backup(BabyBackup("Mia", 100, listOf("DAIRY"), null)))

        assertFalse(repo.isImportInProgress().first())
        assertEquals("DARK", repo.getThemeConfig().first().name)
        assertEquals(30, repo.getPredictiveLeadMinutes().first())
        // baby + onboarding written in the same edit
        val prefs = dataStore.data.first()
        assertEquals("Mia", prefs[stringPreferencesKey("baby_name")])
        assertEquals(true, prefs[booleanPreferencesKey("onboarding_complete")])
        assertEquals(1234L, prefs[longPreferencesKey("import_started_at")])
    }

    @Test
    fun `restore is idempotent`() = runTest {
        val b = backup(null)
        repo.restoreFromBackup(b)
        repo.restoreFromBackup(b)
        assertFalse(repo.isImportInProgress().first())
        assertEquals(12, repo.getMaxPerBreastMinutes().first())
    }

    @Test
    fun `restore clamps out-of-range settings`() = runTest {
        val corrupt = BackupData(
            backupFormatVersion = 1, roomSchemaVersion = 3, appVersion = "1.0.0", exportedAt = 0,
            baby = null,
            settings = SettingsBackup("SYSTEM", 0, 0, 9999, true, true, true, 7, 9999, 9999, true, 9999),
            breastfeeding = emptyList(), sleep = emptyList(), pumping = emptyList(), milkBags = emptyList(),
        )
        repo.restoreFromBackup(corrupt)
        assertEquals(15, repo.getPredictiveLeadMinutes().first())   // collapsed to default
        assertEquals(480, repo.getNapReminderDelayMinutes().first()) // coerced to max
    }
}
