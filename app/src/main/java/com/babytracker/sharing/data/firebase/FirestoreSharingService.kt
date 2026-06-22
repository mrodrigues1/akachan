package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.DiaperSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.InventorySnapshotFields
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSharingService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun shareDoc(code: String): DocumentReference =
        firestore.collection(SHARES).document(code)

    private fun partnersCollection(code: String): CollectionReference =
        shareDoc(code).collection(PARTNERS)

    private suspend fun mergeData(code: String, fields: Map<String, Any?>) {
        shareDoc(code).set(mapOf("data" to fields), SetOptions.merge()).await()
    }

    suspend fun signInAnonymously(): String {
        val result = auth.signInAnonymously().await()
        return checkNotNull(result.user?.uid) { "Anonymous sign-in returned no user" }
    }

    suspend fun createShareDocument(code: String, ownerUid: String) {
        val doc = mapOf(
            "owner" to mapOf(
                "uid" to ownerUid,
                "createdAt" to Timestamp.now(),
            ),
        )
        shareDoc(code).set(doc).await()
    }

    suspend fun isShareCodeValid(code: String): Boolean =
        shareDoc(code).get().await().exists()

    suspend fun syncFullSnapshot(code: String, snapshot: ShareSnapshot) {
        mergeData(code, snapshotToMap(snapshot))
    }

    suspend fun syncSessions(
        code: String,
        sessions: List<SessionSnapshot>,
        prediction: SleepPredictionSnapshot?,
    ) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "sessions" to sessions.map { sessionToMap(it) },
                "sleepPrediction" to prediction?.let { predictionToMap(it) },
            ),
        )
    }

    suspend fun syncSleepRecords(
        code: String,
        sleepRecords: List<SleepSnapshot>,
        prediction: SleepPredictionSnapshot?,
    ) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "sleepRecords" to sleepRecords.map { sleepToMap(it) },
                "sleepPrediction" to prediction?.let { predictionToMap(it) },
            ),
        )
    }

    suspend fun syncBottleFeeds(code: String, bottleFeeds: List<BottleFeedSnapshot>) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "bottleFeeds" to bottleFeeds.map { bottleFeedToMap(it) },
            ),
        )
    }

    suspend fun syncDiapers(code: String, diapers: List<DiaperSnapshot>) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "diapers" to diapers.map { diaperToMap(it) },
            ),
        )
    }

    suspend fun syncBaby(code: String, baby: BabySnapshot) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "baby" to babyToMap(baby),
            ),
        )
    }

    suspend fun syncInventory(
        code: String,
        fields: InventorySnapshotFields,
        milkBags: List<MilkBagSnapshot>,
    ) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "inventoryTotalMl" to fields.totalMl,
                "inventoryBagCount" to fields.bagCount,
                "inventoryUpdatedAt" to fields.updatedAtMs,
                "milkBags" to milkBags.map { milkBagToMap(it) },
            ),
        )
    }

    suspend fun registerPartner(code: String, partnerUid: String) {
        partnersCollection(code).document(partnerUid)
            .set(mapOf("connectedAt" to Timestamp.now())).await()
    }

    suspend fun fetchSnapshot(code: String): ShareSnapshot {
        val doc = shareDoc(code).get().await()
        val data = doc.get("data") as? Map<*, *> ?: error("No data in share document $code")
        return mapToSnapshot(data)
    }

    suspend fun isPartnerConnected(code: String, partnerUid: String): Boolean =
        partnersCollection(code).document(partnerUid)
            .get().await().exists()

    suspend fun getPartners(code: String): List<PartnerInfo> =
        partnersCollection(code).get().await()
            .documents.map { doc ->
                val ts = doc.get("connectedAt") as? Timestamp
                val connectedAt = ts?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
                    ?: Instant.EPOCH
                PartnerInfo(uid = doc.id, connectedAt = connectedAt)
            }

    suspend fun revokePartner(code: String, partnerUid: String) {
        partnersCollection(code).document(partnerUid)
            .delete().await()
    }

    suspend fun deleteShareDocument(code: String) {
        shareDoc(code).delete().await()
    }

    fun observeFeedOps(code: String): Flow<List<FeedOp>> = callbackFlow {
        val registration = shareDoc(code)
            .collection(FEED_OPS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { doc ->
                            mapToFeedOp(doc.id, doc.data ?: return@mapNotNull null)
                        },
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Queues a feed op write and returns once the local Firestore SDK has accepted it —
     * server confirmation is NOT awaited. [WRITE_ACK_TIMEOUT_MS] only bounds how long we wait
     * for the server ack before returning; on timeout the write is still queued and the SDK
     * delivers it when connectivity allows (offline-first by design). Server-side rejections
     * (e.g. security rules) surface asynchronously via [onFailure], possibly after this
     * function has returned.
     */
    suspend fun writeFeedOp(
        code: String,
        op: FeedOp,
        onFailure: (Throwable) -> Unit = {},
    ) {
        val writeTask = shareDoc(code)
            .collection(FEED_OPS).document(op.opId)
            .set(feedOpToMap(op))
            .addOnFailureListener(onFailure)
        withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) {
            writeTask.await()
        }
    }

    fun observeOwnFeedOps(code: String, uid: String): Flow<List<FeedOp>> = callbackFlow {
        val registration = shareDoc(code)
            .collection(FEED_OPS)
            .whereEqualTo("authorUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { doc ->
                            mapToFeedOp(doc.id, doc.data ?: return@mapNotNull null)
                        },
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun deleteFeedOps(code: String, opIds: List<String>) {
        opIds.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { opId ->
                batch.delete(
                    shareDoc(code).collection(FEED_OPS).document(opId),
                )
            }
            batch.commit().await()
        }
    }

    companion object {
        private const val SHARES = "shares"
        private const val PARTNERS = "partners"
        private const val FEED_OPS = "feedOps"
        private const val BATCH_LIMIT = 450
        private const val WRITE_ACK_TIMEOUT_MS = 1_000L
    }
}
