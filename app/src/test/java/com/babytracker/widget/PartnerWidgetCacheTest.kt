package com.babytracker.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class PartnerWidgetCacheTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cache: PartnerWidgetCacheImpl

    private val sample = WidgetData(
        babyName = "Akira",
        lastFeedSide = BreastSide.RIGHT,
        lastFeedStart = Instant.parse("2026-05-24T10:00:00Z"),
        feedState = FeedState.RECENT,
        sleepState = SleepState.SLEEPING,
        sleepSince = Instant.parse("2026-05-24T11:00:00Z"),
    )

    @BeforeEach
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "partner_widget_${System.nanoTime()}.preferences_pb")
        }
        cache = PartnerWidgetCacheImpl(dataStore)
    }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `save then read round-trips all fields for the same share code`() = runTest {
        cache.save("CODE1", sample)

        assertEquals(sample, cache.read("CODE1"))
    }

    @Test
    fun `read returns null on an empty store`() = runTest {
        assertNull(cache.read("CODE1"))
    }

    @Test
    fun `read returns null when the cached share code mismatches`() = runTest {
        cache.save("CODE1", sample)

        assertNull(cache.read("OTHER"))
    }

    @Test
    fun `save then read round-trips null feed and sleep fields`() = runTest {
        cache.save("CODE1", WidgetData.EMPTY)

        assertEquals(WidgetData.EMPTY, cache.read("CODE1"))
    }

    @Test
    fun `clear with the matching code makes read return null`() = runTest {
        cache.save("CODE1", sample)

        cache.clear("CODE1")

        assertNull(cache.read("CODE1"))
    }

    @Test
    fun `clear with a different code leaves the cache intact`() = runTest {
        cache.save("CODE1", sample)

        cache.clear("OTHER")

        assertEquals(sample, cache.read("CODE1"))
    }
}
