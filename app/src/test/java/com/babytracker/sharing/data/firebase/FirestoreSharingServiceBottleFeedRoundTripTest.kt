package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirestoreSharingServiceBottleFeedRoundTripTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val auth = mockk<FirebaseAuth>()
    private val collection = mockk<CollectionReference>()
    private val document = mockk<DocumentReference>()
    private lateinit var service: FirestoreSharingService

    @BeforeEach
    fun setUp() {
        service = FirestoreSharingService(firestore, auth)
        every { firestore.collection("shares") } returns collection
        every { collection.document(any()) } returns document
    }

    @Test
    fun writeLandsUnderDataKeyNotAtRoot() = runTest {
        val mapSlot = slot<Map<String, Any?>>()
        every { document.set(capture(mapSlot), any<SetOptions>()) } returns voidTask()

        service.syncBottleFeeds(
            "CODE1234",
            listOf(BottleFeedSnapshot(timestamp = 1_000L, volumeMl = 120, type = "FORMULA")),
        )

        val written = mapSlot.captured
        assertFalse(written.containsKey("bottleFeeds"), "bottle feeds must not be at root")
        val data = written["data"] as Map<*, *>
        val feed = (data["bottleFeeds"] as List<*>).single() as Map<*, *>
        assertEquals(1_000L, feed["timestamp"])
        assertEquals(120, feed["volumeMl"])
        assertEquals("FORMULA", feed["type"])
    }

    @Test
    fun fetchSnapshotReadsBottleFeedsBack() = runTest {
        val innerData = mapOf<String, Any?>(
            "lastSyncAt" to mockk<Timestamp>(relaxed = true),
            "baby" to mapOf("name" to "", "birthDate" to 0L, "allergies" to emptyList<String>()),
            "sessions" to emptyList<Map<*, *>>(),
            "sleepRecords" to emptyList<Map<*, *>>(),
            // Firestore may widen small ints to Long; mapToBottleFeed must accept Number.
            "bottleFeeds" to listOf(
                mapOf("timestamp" to 1_000L, "volumeMl" to 120L, "type" to "BREAST_MILK"),
            ),
        )
        val docSnapshot = mockk<DocumentSnapshot>()
        every { docSnapshot.get("data") } returns innerData
        every { document.get() } returns docTask(docSnapshot)

        val result = service.fetchSnapshot("CODE1234")

        val feed = result.bottleFeeds.single()
        assertEquals(1_000L, feed.timestamp)
        assertEquals(120, feed.volumeMl)
        assertEquals("BREAST_MILK", feed.type)
    }

    @Test
    fun fetchSnapshotEmptyBottleFeedsWhenAbsent() = runTest {
        val innerData = mapOf<String, Any?>(
            "lastSyncAt" to mockk<Timestamp>(relaxed = true),
            "baby" to mapOf("name" to "", "birthDate" to 0L, "allergies" to emptyList<String>()),
            "sessions" to emptyList<Map<*, *>>(),
            "sleepRecords" to emptyList<Map<*, *>>(),
        )
        val docSnapshot = mockk<DocumentSnapshot>()
        every { docSnapshot.get("data") } returns innerData
        every { document.get() } returns docTask(docSnapshot)

        val result = service.fetchSnapshot("CODE1234")

        assertEquals(emptyList<BottleFeedSnapshot>(), result.bottleFeeds)
    }

    @Test
    fun feedOpToMapOmitsAbsentPayloadKeys() {
        val map = feedOpToMap(
            FeedOp(
                opId = "op-1",
                action = FeedOpAction.DELETE,
                entryClientId = "entry-1",
                authorUid = "partner-uid",
                createdAtMs = 1_000L,
            ),
        )

        assertEquals("delete", map["action"])
        assertEquals("entry-1", map["entryClientId"])
        assertEquals("partner-uid", map["authorUid"])
        assertEquals(1_000L, map["createdAtMs"])
        assertFalse(map.containsKey("timestampMs"))
        assertFalse(map.containsKey("volumeMl"))
        assertFalse(map.containsKey("type"))
        assertFalse(map.containsKey("notes"))
        assertFalse(map.containsKey("consumedBagId"))
    }

    private fun voidTask(): Task<Void> = mockk<Task<Void>>().also {
        every { it.isComplete } returns true
        every { it.isSuccessful } returns true
        every { it.isCanceled } returns false
        every { it.exception } returns null
        every { it.result } returns null
    }

    private fun docTask(snapshot: DocumentSnapshot): Task<DocumentSnapshot> =
        mockk<Task<DocumentSnapshot>>().also {
            every { it.isComplete } returns true
            every { it.isSuccessful } returns true
            every { it.isCanceled } returns false
            every { it.exception } returns null
            every { it.result } returns snapshot
        }
}
