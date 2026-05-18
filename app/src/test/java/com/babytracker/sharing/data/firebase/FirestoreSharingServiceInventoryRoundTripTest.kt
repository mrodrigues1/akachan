package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.InventorySnapshotFields
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

class FirestoreSharingServiceInventoryRoundTripTest {

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

        service.syncInventory(
            "CODE1234",
            InventorySnapshotFields(totalMl = 240, bagCount = 3, updatedAtMs = 12_345L),
        )

        val written = mapSlot.captured
        assertFalse(written.containsKey("inventoryTotalMl"), "inventory fields must not be at root")
        assertFalse(written.containsKey("inventoryBagCount"), "inventory fields must not be at root")
        assertFalse(written.containsKey("inventoryUpdatedAt"), "inventory fields must not be at root")
        val data = written["data"] as Map<*, *>
        assertEquals(240, data["inventoryTotalMl"])
        assertEquals(3, data["inventoryBagCount"])
        assertEquals(12_345L, data["inventoryUpdatedAt"])
    }

    @Test
    fun fetchSnapshotReadsInventoryFieldsBack() = runTest {
        val innerData = mapOf<String, Any?>(
            "lastSyncAt" to mockk<Timestamp>(relaxed = true),
            "baby" to mapOf(
                "name" to "Test",
                "birthDate" to 0L,
                "allergies" to emptyList<String>(),
            ),
            "sessions" to emptyList<Map<*, *>>(),
            "sleepRecords" to emptyList<Map<*, *>>(),
            "inventoryTotalMl" to 240,
            "inventoryBagCount" to 3,
            "inventoryUpdatedAt" to 12_345L,
        )
        val docSnapshot = mockk<DocumentSnapshot>()
        every { docSnapshot.get("data") } returns innerData
        every { document.get() } returns docTask(docSnapshot)

        val result = service.fetchSnapshot("CODE1234")

        assertEquals(240, result.inventoryTotalMl)
        assertEquals(3, result.inventoryBagCount)
        assertEquals(12_345L, result.inventoryUpdatedAt)
    }

    @Test
    fun fetchSnapshotHandlesFirestoreLongAsInt() = runTest {
        // Firestore SDK may return small integers as Long — mapToSnapshot must accept Number
        val innerData = mapOf<String, Any?>(
            "lastSyncAt" to mockk<Timestamp>(relaxed = true),
            "baby" to mapOf("name" to "", "birthDate" to 0L, "allergies" to emptyList<String>()),
            "sessions" to emptyList<Map<*, *>>(),
            "sleepRecords" to emptyList<Map<*, *>>(),
            "inventoryTotalMl" to 120L,
            "inventoryBagCount" to 2L,
            "inventoryUpdatedAt" to 99_999L,
        )
        val docSnapshot = mockk<DocumentSnapshot>()
        every { docSnapshot.get("data") } returns innerData
        every { document.get() } returns docTask(docSnapshot)

        val result = service.fetchSnapshot("CODE1234")

        assertEquals(120, result.inventoryTotalMl)
        assertEquals(2, result.inventoryBagCount)
        assertEquals(99_999L, result.inventoryUpdatedAt)
    }

    @Test
    fun fetchSnapshotNullInventoryFieldsWhenAbsent() = runTest {
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

        assertEquals(null, result.inventoryTotalMl)
        assertEquals(null, result.inventoryBagCount)
        assertEquals(null, result.inventoryUpdatedAt)
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
