package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.GrowthSnapshot
import com.babytracker.sharing.domain.model.MilestoneSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.google.firebase.Timestamp
import java.time.Instant

internal fun snapshotToMap(snapshot: ShareSnapshot): Map<String, Any?> = mapOf(
    "lastSyncAt" to Timestamp(snapshot.lastSyncAt.epochSecond, snapshot.lastSyncAt.nano),
    "baby" to babyToMap(snapshot.baby),
    "sessions" to snapshot.sessions.map { sessionToMap(it) },
    "sleepRecords" to snapshot.sleepRecords.map { sleepToMap(it) },
    "bottleFeeds" to snapshot.bottleFeeds.map { bottleFeedToMap(it) },
    "inventoryTotalMl" to snapshot.inventoryTotalMl,
    "inventoryBagCount" to snapshot.inventoryBagCount,
    "inventoryUpdatedAt" to snapshot.inventoryUpdatedAt,
    "milkBags" to snapshot.milkBags.map { milkBagToMap(it) },
    "sleepPrediction" to snapshot.sleepPrediction?.let { predictionToMap(it) },
    "growth" to snapshot.growth.map { growthToMap(it) },
    "milestones" to snapshot.milestones.map { milestoneToMap(it) },
)

internal fun growthToMap(growth: GrowthSnapshot): Map<String, Any?> = mapOf(
    "type" to growth.type,
    "takenAtMs" to growth.takenAtMs,
    "valueCanonical" to growth.valueCanonical,
    "notes" to growth.notes,
)

internal fun milestoneToMap(milestone: MilestoneSnapshot): Map<String, Any?> = mapOf(
    "title" to milestone.title,
    "dateEpochDay" to milestone.dateEpochDay,
    "timeMinuteOfDay" to milestone.timeMinuteOfDay,
    "note" to milestone.note,
)

internal fun predictionToMap(prediction: SleepPredictionSnapshot): Map<String, Any?> = mapOf(
    "stateLabel" to prediction.stateLabel,
    "windowStart" to prediction.windowStart,
    "windowEnd" to prediction.windowEnd,
    "bestEstimate" to prediction.bestEstimate,
    "confidence" to prediction.confidence,
    "reasons" to prediction.reasons,
    "feedPrompt" to prediction.feedPrompt,
    "generatedAt" to prediction.generatedAt,
)

internal fun babyToMap(baby: BabySnapshot): Map<String, Any> = mapOf(
    "name" to baby.name,
    "birthDate" to baby.birthDateMs,
    "allergies" to baby.allergies,
)

internal fun sessionToMap(session: SessionSnapshot): Map<String, Any?> = mapOf(
    "id" to session.id,
    "startTime" to session.startTime,
    "endTime" to session.endTime,
    "startingSide" to session.startingSide,
    "switchTime" to session.switchTime,
    "pausedDurationMs" to session.pausedDurationMs,
    "notes" to session.notes,
)

internal fun sleepToMap(sleep: SleepSnapshot): Map<String, Any?> = mapOf(
    "id" to sleep.id,
    "startTime" to sleep.startTime,
    "endTime" to sleep.endTime,
    "sleepType" to sleep.sleepType,
    "notes" to sleep.notes,
)

internal fun bottleFeedToMap(feed: BottleFeedSnapshot): Map<String, Any?> = mapOf(
    "timestamp" to feed.timestamp,
    "volumeMl" to feed.volumeMl,
    "type" to feed.type,
    "clientId" to feed.clientId,
    "author" to feed.author,
    "notes" to feed.notes,
)

internal fun milkBagToMap(bag: MilkBagSnapshot): Map<String, Any?> = mapOf(
    "id" to bag.id,
    "collectionDateMs" to bag.collectionDateMs,
    "volumeMl" to bag.volumeMl,
    "notes" to bag.notes,
)

internal fun mapToSnapshot(data: Map<*, *>): ShareSnapshot {
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
    val bottleFeeds = (data["bottleFeeds"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { mapToBottleFeed(it) }
        .orEmpty()
    val milkBags = (data["milkBags"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { mapToMilkBag(it) }
        .orEmpty()
    val sleepPrediction = (data["sleepPrediction"] as? Map<*, *>)?.let { mapToPrediction(it) }
    val growth = (data["growth"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { mapToGrowth(it) }
        .orEmpty()
    val milestones = (data["milestones"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.map { mapToMilestone(it) }
        .orEmpty()
    return ShareSnapshot(
        lastSyncAt = lastSyncAt,
        baby = baby,
        sessions = sessions,
        sleepRecords = sleepRecords,
        bottleFeeds = bottleFeeds,
        inventoryTotalMl = (data["inventoryTotalMl"] as? Number)?.toInt(),
        inventoryBagCount = (data["inventoryBagCount"] as? Number)?.toInt(),
        inventoryUpdatedAt = (data["inventoryUpdatedAt"] as? Number)?.toLong(),
        milkBags = milkBags,
        sleepPrediction = sleepPrediction,
        growth = growth,
        milestones = milestones,
    )
}

internal fun mapToGrowth(map: Map<*, *>): GrowthSnapshot = GrowthSnapshot(
    type = map["type"] as? String ?: "WEIGHT",
    takenAtMs = (map["takenAtMs"] as? Number)?.toLong() ?: 0L,
    valueCanonical = (map["valueCanonical"] as? Number)?.toLong() ?: 0L,
    notes = map["notes"] as? String,
)

internal fun mapToMilestone(map: Map<*, *>): MilestoneSnapshot = MilestoneSnapshot(
    title = map["title"] as? String ?: "",
    dateEpochDay = (map["dateEpochDay"] as? Number)?.toLong() ?: 0L,
    timeMinuteOfDay = (map["timeMinuteOfDay"] as? Number)?.toInt(),
    note = map["note"] as? String,
)

internal fun mapToPrediction(map: Map<*, *>): SleepPredictionSnapshot = SleepPredictionSnapshot(
    stateLabel = map["stateLabel"] as? String ?: "UNAVAILABLE",
    windowStart = (map["windowStart"] as? Number)?.toLong(),
    windowEnd = (map["windowEnd"] as? Number)?.toLong(),
    bestEstimate = (map["bestEstimate"] as? Number)?.toLong(),
    confidence = map["confidence"] as? String,
    reasons = (map["reasons"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
    feedPrompt = map["feedPrompt"] as? String,
    generatedAt = (map["generatedAt"] as? Number)?.toLong() ?: 0L,
)

internal fun mapToBaby(map: Map<*, *>): BabySnapshot = BabySnapshot(
    name = map["name"] as? String ?: "",
    birthDateMs = (map["birthDate"] as? Long) ?: 0L,
    allergies = (map["allergies"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
)

internal fun mapToSession(map: Map<*, *>): SessionSnapshot = SessionSnapshot(
    id = (map["id"] as? Long) ?: 0L,
    startTime = (map["startTime"] as? Long) ?: 0L,
    endTime = map["endTime"] as? Long,
    startingSide = map["startingSide"] as? String ?: "LEFT",
    switchTime = map["switchTime"] as? Long,
    pausedDurationMs = (map["pausedDurationMs"] as? Long) ?: 0L,
    notes = map["notes"] as? String,
)

internal fun mapToSleep(map: Map<*, *>): SleepSnapshot = SleepSnapshot(
    id = (map["id"] as? Long) ?: 0L,
    startTime = (map["startTime"] as? Long) ?: 0L,
    endTime = map["endTime"] as? Long,
    sleepType = map["sleepType"] as? String ?: "NAP",
    notes = map["notes"] as? String,
)

internal fun mapToBottleFeed(map: Map<*, *>): BottleFeedSnapshot = BottleFeedSnapshot(
    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
    volumeMl = (map["volumeMl"] as? Number)?.toInt() ?: 0,
    type = map["type"] as? String ?: "FORMULA",
    clientId = map["clientId"] as? String ?: "",
    author = map["author"] as? String ?: "OWNER",
    notes = map["notes"] as? String,
)

internal fun mapToMilkBag(map: Map<*, *>): MilkBagSnapshot = MilkBagSnapshot(
    id = (map["id"] as? Number)?.toLong() ?: 0L,
    collectionDateMs = (map["collectionDateMs"] as? Number)?.toLong() ?: 0L,
    volumeMl = (map["volumeMl"] as? Number)?.toInt() ?: 0,
    notes = map["notes"] as? String,
)

// Unknown action or missing required fields -> null -> op skipped, never crash the listener.
internal fun mapToFeedOp(opId: String, map: Map<*, *>): FeedOp? {
    val action = (map["action"] as? String)?.let { raw ->
        FeedOpAction.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    } ?: return null
    return FeedOp(
        opId = opId,
        action = action,
        entryClientId = map["entryClientId"] as? String ?: return null,
        authorUid = map["authorUid"] as? String ?: return null,
        createdAtMs = (map["createdAtMs"] as? Number)?.toLong() ?: return null,
        timestampMs = (map["timestampMs"] as? Number)?.toLong(),
        volumeMl = (map["volumeMl"] as? Number)?.toInt(),
        type = map["type"] as? String,
        notes = map["notes"] as? String,
        consumedBagId = (map["consumedBagId"] as? Number)?.toLong(),
    )
}

internal fun feedOpToMap(op: FeedOp): Map<String, Any?> = buildMap {
    put("action", op.action.name.lowercase())
    put("entryClientId", op.entryClientId)
    put("authorUid", op.authorUid)
    put("createdAtMs", op.createdAtMs)
    op.timestampMs?.let { put("timestampMs", it) }
    op.volumeMl?.let { put("volumeMl", it) }
    op.type?.let { put("type", it) }
    op.notes?.let { put("notes", it) }
    op.consumedBagId?.let { put("consumedBagId", it) }
}
