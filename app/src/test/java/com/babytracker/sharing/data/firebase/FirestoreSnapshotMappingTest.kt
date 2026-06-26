package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.GrowthSnapshot
import com.babytracker.sharing.domain.model.MilestoneSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepSnapshot
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
    fun `snapshot with new sleep fields round-trips clientId and startedBy through map`() {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.ofEpochSecond(100),
            baby = BabySnapshot("Aiko", 1000L, listOf("dairy")),
            sessions = emptyList(),
            sleepRecords = listOf(
                SleepSnapshot(
                    id = 9,
                    startTime = 3000L,
                    endTime = null,
                    sleepType = "NIGHT_SLEEP",
                    notes = "down for the night",
                    clientId = "sleep-client-1",
                    startedBy = "PARTNER",
                ),
            ),
        )

        val roundTripped = mapToSnapshot(snapshotToMap(snapshot))

        assertEquals(snapshot.sleepRecords, roundTripped.sleepRecords)
    }

    @Test
    fun `legacy sleep map without new keys parses with safe defaults`() {
        val legacy = mapOf("id" to 1L, "startTime" to 2000L, "sleepType" to "NAP")

        val parsed = mapToSleep(legacy)

        assertEquals("", parsed.clientId)
        assertEquals("OWNER", parsed.startedBy)
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
    fun `feed op map with all required fields parses`() {
        val parsed = mapToFeedOp("op-1", feedOpMap())

        assertEquals("op-1", parsed?.opId)
        assertEquals("partner-uid", parsed?.authorUid)
    }

    @Test
    fun `feed op map without authorUid is rejected`() {
        val parsed = mapToFeedOp("op-1", feedOpMap() - "authorUid")

        assertEquals(null, parsed)
    }

    @Test
    fun `sleep op round-trips with lowercase action and sleepType on the wire`() {
        val op = SleepOp(
            opId = "s-1",
            action = SleepOpAction.START,
            entryClientId = "client-1",
            authorUid = "partner-uid",
            createdAtMs = 1000L,
            startTimeMs = 2000L,
            sleepType = "NIGHT_SLEEP",
            notes = "down",
        )

        val map = sleepOpToMap(op)
        assertEquals("start", map["action"])
        assertEquals("night_sleep", map["sleepType"])

        val parsed = mapToSleepOp("s-1", map)
        assertEquals(op, parsed)
    }

    @Test
    fun `sleep op map with unknown action is rejected`() {
        val parsed = mapToSleepOp("s-1", sleepOpMap() + ("action" to "drop"))
        assertEquals(null, parsed)
    }

    @Test
    fun `sleep op map without createdAtMs is rejected`() {
        val parsed = mapToSleepOp("s-1", sleepOpMap() - "createdAtMs")
        assertEquals(null, parsed)
    }

    private fun sleepOpMap(): Map<String, Any?> = mapOf(
        "action" to "stop",
        "entryClientId" to "client-1",
        "authorUid" to "partner-uid",
        "createdAtMs" to 1000L,
        "endTimeMs" to 2000L,
    )

    private fun feedOpMap(): Map<String, Any?> = mapOf(
        "action" to "delete",
        "entryClientId" to "client-1",
        "authorUid" to "partner-uid",
        "createdAtMs" to 1000L,
    )

    @Test
    fun `legacy snapshot map without milkBags key parses to empty list`() {
        val legacy = mapOf<String, Any?>(
            "baby" to mapOf("name" to "", "birthDate" to 0L, "allergies" to emptyList<String>()),
        )

        val parsed = mapToSnapshot(legacy)

        assertEquals(emptyList<MilkBagSnapshot>(), parsed.milkBags)
    }

    @Test
    fun `growth and milestones round-trip through the map`() {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.ofEpochSecond(100),
            baby = BabySnapshot("Aiko", 1000L, emptyList()),
            sessions = emptyList(),
            sleepRecords = emptyList(),
            growth = listOf(
                GrowthSnapshot(type = "WEIGHT", takenAtMs = 2000L, valueCanonical = 5200, notes = "checkup"),
                GrowthSnapshot(type = "LENGTH", takenAtMs = 3000L, valueCanonical = 600, notes = null),
            ),
            milestones = listOf(
                MilestoneSnapshot(title = "First steps", dateEpochDay = 20000L, timeMinuteOfDay = 510, note = "in the park"),
                MilestoneSnapshot(title = "Beach day", dateEpochDay = 20100L, timeMinuteOfDay = null, note = null),
            ),
        )

        val roundTripped = mapToSnapshot(snapshotToMap(snapshot))

        assertEquals(snapshot.growth, roundTripped.growth)
        assertEquals(snapshot.milestones, roundTripped.milestones)
    }

    @Test
    fun `legacy snapshot map without growth or milestones keys parses to empty lists`() {
        val legacy = mapOf<String, Any?>(
            "baby" to mapOf("name" to "", "birthDate" to 0L, "allergies" to emptyList<String>()),
        )

        val parsed = mapToSnapshot(legacy)

        assertEquals(emptyList<GrowthSnapshot>(), parsed.growth)
        assertEquals(emptyList<MilestoneSnapshot>(), parsed.milestones)
    }
}
