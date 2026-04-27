package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSharingService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
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
        firestore.collection(SHARES).document(code).set(doc).await()
    }

    suspend fun isShareCodeValid(code: String): Boolean =
        firestore.collection(SHARES).document(code).get().await().exists()

    suspend fun syncFullSnapshot(code: String, snapshot: ShareSnapshot) {
        firestore.collection(SHARES).document(code)
            .set(mapOf("data" to snapshotToMap(snapshot)), SetOptions.merge()).await()
    }

    suspend fun syncSessions(code: String, sessions: List<SessionSnapshot>) {
        val data = mapOf(
            "data" to mapOf(
                "lastSyncAt" to Timestamp.now(),
                "sessions" to sessions.map { sessionToMap(it) },
            ),
        )
        firestore.collection(SHARES).document(code).set(data, SetOptions.merge()).await()
    }

    suspend fun syncSleepRecords(code: String, sleepRecords: List<SleepSnapshot>) {
        val data = mapOf(
            "data" to mapOf(
                "lastSyncAt" to Timestamp.now(),
                "sleepRecords" to sleepRecords.map { sleepToMap(it) },
            ),
        )
        firestore.collection(SHARES).document(code).set(data, SetOptions.merge()).await()
    }

    suspend fun syncBaby(code: String, baby: BabySnapshot) {
        val data = mapOf(
            "data" to mapOf(
                "lastSyncAt" to Timestamp.now(),
                "baby" to babyToMap(baby),
            ),
        )
        firestore.collection(SHARES).document(code).set(data, SetOptions.merge()).await()
    }

    suspend fun registerPartner(code: String, partnerUid: String) {
        firestore.collection(SHARES).document(code)
            .collection(PARTNERS).document(partnerUid)
            .set(mapOf("connectedAt" to Timestamp.now())).await()
    }

    suspend fun fetchSnapshot(code: String): ShareSnapshot {
        val doc = firestore.collection(SHARES).document(code).get().await()
        val data = doc.get("data") as? Map<*, *> ?: error("No data in share document $code")
        return mapToSnapshot(data)
    }

    suspend fun isPartnerConnected(code: String, partnerUid: String): Boolean =
        firestore.collection(SHARES).document(code)
            .collection(PARTNERS).document(partnerUid)
            .get().await().exists()

    suspend fun getPartners(code: String): List<PartnerInfo> =
        firestore.collection(SHARES).document(code)
            .collection(PARTNERS).get().await()
            .documents.map { doc ->
                val ts = doc.get("connectedAt") as? Timestamp
                val connectedAt = ts?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
                    ?: Instant.EPOCH
                PartnerInfo(uid = doc.id, connectedAt = connectedAt)
            }

    suspend fun revokePartner(code: String, partnerUid: String) {
        firestore.collection(SHARES).document(code)
            .collection(PARTNERS).document(partnerUid)
            .delete().await()
    }

    suspend fun deleteShareDocument(code: String) {
        firestore.collection(SHARES).document(code).delete().await()
    }

    private fun snapshotToMap(snapshot: ShareSnapshot): Map<String, Any?> = mapOf(
        "lastSyncAt" to Timestamp(snapshot.lastSyncAt.epochSecond, snapshot.lastSyncAt.nano),
        "baby" to babyToMap(snapshot.baby),
        "sessions" to snapshot.sessions.map { sessionToMap(it) },
        "sleepRecords" to snapshot.sleepRecords.map { sleepToMap(it) },
    )

    private fun babyToMap(baby: BabySnapshot): Map<String, Any> = mapOf(
        "name" to baby.name,
        "birthDate" to baby.birthDateMs,
        "allergies" to baby.allergies,
    )

    private fun sessionToMap(session: SessionSnapshot): Map<String, Any?> = mapOf(
        "id" to session.id,
        "startTime" to session.startTime,
        "endTime" to session.endTime,
        "startingSide" to session.startingSide,
        "switchTime" to session.switchTime,
        "pausedDurationMs" to session.pausedDurationMs,
        "notes" to session.notes,
    )

    private fun sleepToMap(sleep: SleepSnapshot): Map<String, Any?> = mapOf(
        "id" to sleep.id,
        "startTime" to sleep.startTime,
        "endTime" to sleep.endTime,
        "sleepType" to sleep.sleepType,
        "notes" to sleep.notes,
    )

    private fun mapToSnapshot(data: Map<*, *>): ShareSnapshot {
        val ts = data["lastSyncAt"] as? Timestamp
        val lastSyncAt = ts?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
            ?: Instant.EPOCH
        val baby = (data["baby"] as? Map<*, *>)?.let { mapToBaby(it) }
            ?: BabySnapshot("", 0L, emptyList())
        val sessions = (data["sessions"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map { mapToSession(it) }
            .orEmpty()
        val sleepRecords = (data["sleepRecords"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map { mapToSleep(it) }
            .orEmpty()
        return ShareSnapshot(lastSyncAt, baby, sessions, sleepRecords)
    }

    private fun mapToBaby(map: Map<*, *>): BabySnapshot = BabySnapshot(
        name = map["name"] as? String ?: "",
        birthDateMs = (map["birthDate"] as? Long) ?: 0L,
        allergies = (map["allergies"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
    )

    private fun mapToSession(map: Map<*, *>): SessionSnapshot = SessionSnapshot(
        id = (map["id"] as? Long) ?: 0L,
        startTime = (map["startTime"] as? Long) ?: 0L,
        endTime = map["endTime"] as? Long,
        startingSide = map["startingSide"] as? String ?: "LEFT",
        switchTime = map["switchTime"] as? Long,
        pausedDurationMs = (map["pausedDurationMs"] as? Long) ?: 0L,
        notes = map["notes"] as? String,
    )

    private fun mapToSleep(map: Map<*, *>): SleepSnapshot = SleepSnapshot(
        id = (map["id"] as? Long) ?: 0L,
        startTime = (map["startTime"] as? Long) ?: 0L,
        endTime = map["endTime"] as? Long,
        sleepType = map["sleepType"] as? String ?: "NAP",
        notes = map["notes"] as? String,
    )

    companion object {
        private const val SHARES = "shares"
        private const val PARTNERS = "partners"
    }
}
