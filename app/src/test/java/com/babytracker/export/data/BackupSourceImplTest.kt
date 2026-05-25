package com.babytracker.export.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BackupSourceImplTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dsScope: CoroutineScope
    private lateinit var dsFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var source: BackupSourceImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, BabyTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dsFile = File(context.cacheDir, "backup_test_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dsScope) { dsFile }
        source = BackupSourceImpl(db, dataStore)
    }

    @After
    fun tearDown() {
        db.close()
        dsScope.cancel()
        dsFile.delete()
    }

    @Test
    fun `readTracking captures all tables with resolvable milk bag FK`() = runTest {
        val pumpId = db.pumpingDao().insert(PumpingEntity(startTime = 1, endTime = 2, breast = "LEFT"))
        db.milkBagDao().insert(
            MilkBagEntity(collectionDate = 1, volumeMl = 10, sourceSessionId = pumpId, createdAt = 1),
        )
        val tracking = source.readTracking()
        assertEquals(1, tracking.pumping.size)
        assertEquals(1, tracking.milkBags.size)
        assertTrue(tracking.pumping.any { it.id == tracking.milkBags.first().sourceSessionId })
    }

    @Test
    fun `readPreferences maps baby and settings from stored prefs`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("baby_name")] = "Mia"
            it[longPreferencesKey("baby_birth_date")] = 100L
            it[stringPreferencesKey("allergies")] = "DAIRY,EGG"
            it[intPreferencesKey("max_per_breast_minutes")] = 15
            it[stringPreferencesKey("theme_config")] = "DARK"
        }
        val prefs = source.readPreferences()
        assertEquals("Mia", prefs.baby?.name)
        assertEquals(listOf("DAIRY", "EGG"), prefs.baby?.allergies)
        assertEquals(15, prefs.settings.maxPerBreastMinutes)
        assertEquals("DARK", prefs.settings.themeConfig)
    }

    @Test
    fun `readPreferences returns null baby and default settings when empty`() = runTest {
        val prefs = source.readPreferences()
        assertNull(prefs.baby)
        assertEquals("SYSTEM", prefs.settings.themeConfig)
        assertEquals(0, prefs.settings.maxPerBreastMinutes)
        assertEquals(true, prefs.settings.autoUpdateEnabled)
        assertEquals(480, prefs.settings.quietHoursEndMinute)
        assertNull(prefs.settings.wakeTimeMinuteOfDay)
    }

    @Test
    fun `readPreferences sanitizes corrupted stored settings`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("theme_config")] = "NONSENSE"   // unknown enum
            it[intPreferencesKey("predictive_lead_minutes")] = 7    // not in {5,10,15,30}
            it[intPreferencesKey("wake_time_minutes")] = 5000       // out of 0..1439
            it[intPreferencesKey("quiet_hours_start_minute")] = -10 // below range
            it[intPreferencesKey("nap_reminder_delay_minutes")] = 999 // above 480
        }
        val s = source.readPreferences().settings
        assertEquals("SYSTEM", s.themeConfig)
        assertEquals(15, s.predictiveLeadMinutes)
        assertNull(s.wakeTimeMinuteOfDay)
        assertEquals(0, s.quietHoursStartMinute)
        assertEquals(480, s.napReminderDelayMinutes)
    }
}
