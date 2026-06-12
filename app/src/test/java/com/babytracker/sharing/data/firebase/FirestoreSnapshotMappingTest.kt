package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class FirestoreSnapshotMappingTest {

    @Test
    fun `snapshot with new feed fields and milk bags round-trips through map`() {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.ofEpochSecond(100),
            baby = BabySnapshot("Aiko", 1000L, listOf("dairy")),
            sessions = emptyList(),
            sleepRecords = emptyList(),
            bottleFeeds = listOf(
                BottleFeedSnapshot(
                    timestamp = 2000L,
                    volumeMl = 120,
                    type = "BREAST_MILK",
                    clientId = "client-1",
                    author = "PARTNER",
                    notes = "evening",
                ),
            ),
            milkBags = listOf(MilkBagSnapshot(id = 7, collectionDateMs = 5000L, volumeMl = 150, notes = null)),
        )

        val roundTripped = mapToSnapshot(snapshotToMap(snapshot))

        assertEquals(snapshot.bottleFeeds, roundTripped.bottleFeeds)
        assertEquals(snapshot.milkBags, roundTripped.milkBags)
    }

    @Test
    fun `legacy bottle feed map without new keys parses with safe defaults`() {
        val legacy = mapOf("timestamp" to 2000L, "volumeMl" to 120, "type" to "FORMULA")

        val parsed = mapToBottleFeed(legacy)

        assertEquals("", parsed.clientId)
        assertEquals("OWNER", parsed.author)
        assertEquals(null, parsed.notes)
    }

    @Test
    fun `legacy snapshot map without milkBags key parses to empty list`() {
        val legacy = mapOf<String, Any?>(
            "baby" to mapOf("name" to "", "birthDate" to 0L, "allergies" to emptyList<String>()),
        )

        val parsed = mapToSnapshot(legacy)

        assertEquals(emptyList<MilkBagSnapshot>(), parsed.milkBags)
    }
}
