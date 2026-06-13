package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.babytracker.domain.model.HomeTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryHomeTileTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "home_tile_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() { scope.cancel(); file.delete() }

    @Test
    fun `default order returned when nothing stored`() = runTest {
        assertEquals(HomeTile.DEFAULT_ORDER, repository.getHomeTileOrder().first())
    }

    @Test
    fun `set then get round-trips custom order`() = runTest {
        val custom = listOf(HomeTile.PARTNER, HomeTile.SLEEP) +
            HomeTile.DEFAULT_ORDER.filter { it != HomeTile.PARTNER && it != HomeTile.SLEEP }
        repository.setHomeTileOrder(custom)
        assertEquals(custom, repository.getHomeTileOrder().first())
    }
}
