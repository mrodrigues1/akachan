package com.babytracker.sharing.data.firebase

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.GrowthSnapshot
import com.babytracker.sharing.domain.model.MilestoneSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.PredictionStateLabel
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                    sleepType = SleepType.NIGHT_SLEEP,
                    notes = "down for the night",
                    clientId = "sleep-client-1",
                    startedBy = SleepAuthor.PARTNER,
                ),
            ),
        )

        val roundTripped = mapToSnapshot(snapshotToMap(snapshot))

        assertEquals(snapshot.sleepRecords, roundTripped.sleepRecords)
    }

    @Test
    fun `snapshot sleep record keeps the exact uppercase sleepType and startedBy on the wire`() {
        val record = SleepSnapshot(
            id = 9,
            startTime = 3000L,
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
            notes = null,
            clientId = "sleep-client-1",
            startedBy = SleepAuthor.PARTNER,
        )

        val map = sleepToMap(record)

        // WIRE CONTRACT: snapshot records ship SleepType.name / SleepAuthor.name (uppercase) unchanged.
        assertEquals("NIGHT_SLEEP", map["sleepType"])
        assertEquals("PARTNER", map["startedBy"])
        // And a wire map deserializes those exact strings back to the typed values.
        val parsed = mapToSleep(map)
        assertEquals(SleepType.NIGHT_SLEEP, parsed.sleepType)
        assertEquals(SleepAuthor.PARTNER, parsed.startedBy)
    }

    @Test
    fun `session round-trips pausedAtMs through map`() {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.ofEpochSecond(100),
            baby = BabySnapshot("Aiko", 1000L, listOf("dairy")),
            sessions = listOf(
                SessionSnapshot(
                    id = 1L,
                    startTime = 1000L,
                    endTime = null,
                    startingSide = "LEFT",
                    switchTime = null,
                    pausedDurationMs = 0L,
                    notes = null,
                    pausedAtMs = 4200L,
                ),
            ),
            sleepRecords = emptyList(),
        )

        val roundTripped = mapToSnapshot(snapshotToMap(snapshot))

        assertEquals(snapshot.sessions, roundTripped.sessions)
        assertEquals(4200L, roundTripped.sessions.single().pausedAtMs)
    }

    @Test
    fun `legacy session map without pausedAtMs parses as running`() {
        val legacy = mapOf("id" to 1L, "startTime" to 2000L, "startingSide" to "LEFT", "pausedDurationMs" to 0L)

        val parsed = mapToSession(legacy)

        assertEquals(null, parsed.pausedAtMs)
        assertEquals(false, parsed.isPaused)
    }

    @Test
    fun `legacy sleep map without new keys parses with safe defaults`() {
        val legacy = mapOf("id" to 1L, "startTime" to 2000L, "sleepType" to "NAP")

        val parsed = mapToSleep(legacy)

        assertEquals(SleepType.NAP, parsed.sleepType)
        assertEquals("", parsed.clientId)
        assertEquals(SleepAuthor.OWNER, parsed.startedBy)
    }

    @Test
    fun `sleep map with unknown sleepType or startedBy falls back to NAP and OWNER`() {
        val garbage = mapOf(
            "id" to 1L,
            "startTime" to 2000L,
            "sleepType" to "siesta",
            "startedBy" to "stranger",
        )

        val parsed = mapToSleep(garbage)

        assertEquals(SleepType.NAP, parsed.sleepType)
        assertEquals(SleepAuthor.OWNER, parsed.startedBy)
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
            sleepType = SleepType.NIGHT_SLEEP,
            notes = "down",
        )

        val map = sleepOpToMap(op)
        // WIRE CONTRACT: ops ship the action + sleepType lowercased.
        assertEquals("start", map["action"])
        assertEquals("night_sleep", map["sleepType"])

        val parsed = mapToSleepOp("s-1", map)
        assertEquals(op, parsed)
    }

    @Test
    fun `sleep op serializes NAP to lowercase and deserializes lowercase wire back to the enum`() {
        val op = SleepOp(
            opId = "s-2",
            action = SleepOpAction.START,
            entryClientId = "client-1",
            authorUid = "partner-uid",
            createdAtMs = 1000L,
            startTimeMs = 2000L,
            sleepType = SleepType.NAP,
        )

        assertEquals("nap", sleepOpToMap(op)["sleepType"])
        assertEquals(SleepType.NAP, mapToSleepOp("s-2", mapOf(
            "action" to "start",
            "entryClientId" to "client-1",
            "authorUid" to "partner-uid",
            "createdAtMs" to 1000L,
            "startTimeMs" to 2000L,
            "sleepType" to "nap",
        ))?.sleepType)
    }

    @Test
    fun `sleep op with an unrecognized wire sleepType parses to a null typed field`() {
        val parsed = mapToSleepOp("s-3", sleepOpMap() + ("sleepType" to "siesta"))
        assertEquals(null, parsed?.sleepType)
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

    @Test
    fun `prediction stateLabel serializes to exactly the wire literal each enum value has always had`() {
        // WIRE CONTRACT: PredictionStateLabel.name must equal the literal the producer emitted
        // before the enum existed — bytes on the wire are unchanged.
        val expectedWireLiterals = mapOf(
            PredictionStateLabel.WINDOW to "WINDOW",
            PredictionStateLabel.NEED_MORE_DATA to "NEED_MORE_DATA",
            PredictionStateLabel.CUE_LED to "CUE_LED",
            PredictionStateLabel.CURRENTLY_SLEEPING to "CURRENTLY_SLEEPING",
            PredictionStateLabel.AFTER_ACTIVE_FEED to "AFTER_ACTIVE_FEED",
            PredictionStateLabel.OVERDUE to "OVERDUE",
        )

        expectedWireLiterals.forEach { (label, wireLiteral) ->
            val prediction = SleepPredictionSnapshot(stateLabel = label, generatedAt = 42L)
            assertEquals(wireLiteral, predictionToMap(prediction)["stateLabel"])
        }
    }

    @Test
    fun `prediction map with an unrecognized wire stateLabel reads back as UNAVAILABLE`() {
        val map = mapOf("stateLabel" to "FROM_A_NEWER_APP_VERSION", "generatedAt" to 42L)

        assertEquals(PredictionStateLabel.UNAVAILABLE, mapToPrediction(map).stateLabel)
    }

    @Test
    fun `prediction map missing stateLabel reads back as UNAVAILABLE`() {
        val map = mapOf<String, Any?>("generatedAt" to 42L)

        assertEquals(PredictionStateLabel.UNAVAILABLE, mapToPrediction(map).stateLabel)
    }

    @Test
    fun `prediction round-trips every reason variant and feedDue through the map`() {
        val prediction = SleepPredictionSnapshot(
            stateLabel = PredictionStateLabel.WINDOW,
            windowStart = 1_000L,
            windowEnd = 2_000L,
            bestEstimate = 1_500L,
            confidence = "HIGH",
            reasons = listOf(
                SleepReason.FullyPersonalized(SleepType.NIGHT_SLEEP),
                SleepReason.Blended(percent = 60, nextType = SleepType.NAP),
                SleepReason.TypicalWakeWindow(ageInWeeks = 12, minMinutes = 60L, maxMinutes = 90L),
                SleepReason.TypeSpecificPattern(nextType = SleepType.NAP, intervalCount = 5),
                SleepReason.CombinedHistory(SleepType.NIGHT_SLEEP),
                SleepReason.Disruption,
                SleepReason.CircadianSlot,
                SleepReason.NapDeficit(deficit = 2),
                SleepReason.SleepDebt(earlierWindow = true),
            ),
            feedDue = true,
            generatedAt = 42L,
        )

        assertEquals(prediction, mapToPrediction(predictionToMap(prediction)))
    }

    @Test
    fun `unknown reason types are dropped instead of failing the whole prediction`() {
        val map = mapOf(
            "stateLabel" to "WINDOW",
            "reasons" to listOf(mapOf("type" to "FROM_A_NEWER_APP_VERSION"), mapOf("type" to "DISRUPTION")),
            "generatedAt" to 42L,
        )

        assertEquals(listOf(SleepReason.Disruption), mapToPrediction(map).reasons)
    }

    @Test
    fun `legacy prediction map with localized string reasons parses to an empty semantic list`() {
        val legacy = mapOf(
            "stateLabel" to "WINDOW",
            "reasons" to listOf("Awake 2h 05m"),
            "feedPrompt" to "A breastfeed may be due near this window.",
            "generatedAt" to 42L,
        )

        val parsed = mapToPrediction(legacy)

        assertEquals(emptyList<SleepReason>(), parsed.reasons)
        assertFalse(parsed.feedDue)
    }
}
