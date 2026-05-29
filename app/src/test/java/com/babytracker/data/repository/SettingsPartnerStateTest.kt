package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsPartnerStateTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "partner_state_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repo = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() { scope.cancel(); file.delete() }

    @Test
    fun `clears and returns true when stored code matches`() = runTest {
        repo.setShareCode("CODE_A")
        repo.setAppMode(AppMode.PARTNER)

        val cleared = repo.clearPartnerStateIfShareCodeMatches("CODE_A")

        assertTrue(cleared)
        assertNull(repo.getShareCode().first())
        assertEquals(AppMode.NONE, repo.getAppMode().first())
    }

    @Test
    fun `leaves state and returns false when stored code differs`() = runTest {
        repo.setShareCode("CODE_B")
        repo.setAppMode(AppMode.PARTNER)

        val cleared = repo.clearPartnerStateIfShareCodeMatches("CODE_A")

        assertFalse(cleared)
        assertEquals("CODE_B", repo.getShareCode().first())
        assertEquals(AppMode.PARTNER, repo.getAppMode().first())
    }
}
