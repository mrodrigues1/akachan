package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.DiaperSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.SleepOp
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
import com.google.firebase.firestore.Source
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
    // internal (not private) so the sleep-op extension functions below can reuse them without
    // inflating this class's member-function count.
    internal val firestore: FirebaseFirestore,
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

    /**
     * Writes the bottle-feed and inventory field groups in a single `mergeData` round-trip. The
     * field set is exactly the union of [syncBottleFeeds] + [syncInventory], so the resulting
     * document shape is identical to issuing those two syncs back-to-back — only the round-trip
     * count drops from two to one.
     */
    suspend fun syncBottleFeedsAndInventory(
        code: String,
        bottleFeeds: List<BottleFeedSnapshot>,
        fields: InventorySnapshotFields,
        milkBags: List<MilkBagSnapshot>,
    ) {
        mergeData(
            code,
            mapOf(
                "lastSyncAt" to Timestamp.now(),
                "bottleFeeds" to bottleFeeds.map { bottleFeedToMap(it) },
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

    /**
     * Firestore does not cascade-delete subcollections, so drain partners/feedOps/sleepOps
     * before deleting the share document itself — otherwise their documents stay in Firestore
     * forever, unreachable but billed, and resurface if the code is ever reused.
     */
    suspend fun deleteShareDocument(code: String) {
        drainSubcollection(partnersCollection(code))
        drainSubcollection(shareDoc(code).collection(FEED_OPS))
        drainSubcollection(shareDoc(code).collection(SLEEP_OPS))
        shareDoc(code).delete().await()
    }

    // Server-sourced like getFeedOps: a cache-first read could miss partner-written docs and
    // leave orphans behind.
    private suspend fun drainSubcollection(collection: CollectionReference) {
        collection.get(Source.SERVER).await()
            .documents.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
    }

    /**
     * Observes the feed-ops subcollection. With [authorUid] null, emits every op; with it set,
     * filters to that author's own ops (`whereEqualTo("authorUid", …)`).
     */
    fun observeFeedOps(code: String, authorUid: String? = null): Flow<List<FeedOp>> = callbackFlow {
        val base = shareDoc(code).collection(FEED_OPS)
        val query = if (authorUid != null) base.whereEqualTo("authorUid", authorUid) else base
        val registration = query.addSnapshotListener { snapshot, error ->
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

    /**
     * One-shot fetch of all pending feed ops for the background drain worker. Forces
     * [Source.SERVER] so a freshly-written op isn't missed by a cache-first read: the very op
     * the drain exists to apply may not be in this device's local cache yet (it woke from the
     * background and never opened a live listener). Throws if the server is unreachable; the
     * worker treats that as retryable.
     */
    suspend fun getFeedOps(code: String): List<FeedOp> =
        shareDoc(code).collection(FEED_OPS).get(Source.SERVER).await()
            .documents.mapNotNull { doc -> mapToFeedOp(doc.id, doc.data ?: return@mapNotNull null) }

    companion object {
        internal const val SHARES = "shares"
        internal const val PARTNERS = "partners"
        private const val FEED_OPS = "feedOps"
        internal const val SLEEP_OPS = "sleepOps"
        internal const val BATCH_LIMIT = 450
        internal const val WRITE_ACK_TIMEOUT_MS = 1_000L
    }
}

// Sleep-op subcollection operations, kept as extension functions so they don't inflate the service's
// member-function count (detekt TooManyFunctions). They mirror the feed-op methods above and reach
// the subcollection via the public `firestore` property (no private member access).
private fun FirestoreSharingService.sleepOps(code: String) =
    firestore.collection(FirestoreSharingService.SHARES).document(code)
        .collection(FirestoreSharingService.SLEEP_OPS)

/** Observes the sleep-ops subcollection. [authorUid] null = every op; set = that author's own. */
fun FirestoreSharingService.observeSleepOps(code: String, authorUid: String? = null): Flow<List<SleepOp>> =
    callbackFlow {
        val base = sleepOps(code)
        val query = if (authorUid != null) base.whereEqualTo("authorUid", authorUid) else base
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        mapToSleepOp(doc.id, doc.data ?: return@mapNotNull null)
                    },
                )
            }
        }
        awaitClose { registration.remove() }
    }

/** Offline-first like writeFeedOp: queues the write, server ack is not awaited beyond the timeout. */
suspend fun FirestoreSharingService.writeSleepOp(
    code: String,
    op: SleepOp,
    onFailure: (Throwable) -> Unit = {},
) {
    val writeTask = sleepOps(code).document(op.opId)
        .set(sleepOpToMap(op))
        .addOnFailureListener(onFailure)
    withTimeoutOrNull(FirestoreSharingService.WRITE_ACK_TIMEOUT_MS) {
        writeTask.await()
    }
}

suspend fun FirestoreSharingService.deleteSleepOps(code: String, opIds: List<String>) {
    opIds.chunked(FirestoreSharingService.BATCH_LIMIT).forEach { chunk ->
        val batch = firestore.batch()
        chunk.forEach { opId ->
            batch.delete(sleepOps(code).document(opId))
        }
        batch.commit().await()
    }
}

/** One-shot server fetch of all pending sleep ops, mirroring [FirestoreSharingService.getFeedOps]
 *  for the background drain worker. Server-sourced so a just-written op isn't missed by a cache read. */
suspend fun FirestoreSharingService.getSleepOps(code: String): List<SleepOp> =
    sleepOps(code).get(Source.SERVER).await()
        .documents.mapNotNull { doc -> mapToSleepOp(doc.id, doc.data ?: return@mapNotNull null) }

/** A share-document emission with its cache origin, so callers can distinguish a server-confirmed
 *  absence (document gone) from a cache-origin one (cold offline start). */
data class SnapshotEmission(val data: ShareSnapshot?, val fromCache: Boolean)

/** A partner-connection emission with its cache origin. [connected] = the partner doc exists. */
data class ConnectionEmission(val connected: Boolean, val fromCache: Boolean)

/**
 * Streams the share document. [SnapshotEmission.data] is the mapped snapshot when the `data` field is
 * present, else null (document missing / no data). Mirrors [FirestoreSharingService.observeFeedOps]:
 * close on listener error, remove the registration on cancellation.
 */
fun FirestoreSharingService.observeSnapshot(code: String): Flow<SnapshotEmission> = callbackFlow {
    val registration = firestore.collection(FirestoreSharingService.SHARES).document(code)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val data = snapshot.get("data") as? Map<*, *>
                trySend(
                    SnapshotEmission(
                        data = data?.let { mapToSnapshot(it) },
                        fromCache = snapshot.metadata.isFromCache,
                    ),
                )
            }
        }
    awaitClose { registration.remove() }
}

/** The live equivalent of [FirestoreSharingService.isPartnerConnected]: streams whether the partner's
 *  own `partners/{partnerUid}` document exists (readable per rules: request.auth.uid == partnerUid). */
fun FirestoreSharingService.observePartnerConnected(
    code: String,
    partnerUid: String,
): Flow<ConnectionEmission> = callbackFlow {
    val registration = firestore.collection(FirestoreSharingService.SHARES).document(code)
        .collection(FirestoreSharingService.PARTNERS).document(partnerUid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(ConnectionEmission(connected = snapshot.exists(), fromCache = snapshot.metadata.isFromCache))
            }
        }
    awaitClose { registration.remove() }
}
