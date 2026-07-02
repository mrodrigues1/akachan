package com.babytracker.sharing.data.firebase

import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.toSleepTypeSafe
import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.DiaperSnapshot
import com.babytracker.sharing.domain.model.DoctorVisitSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.GrowthSnapshot
import com.babytracker.sharing.domain.model.MilestoneSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import com.babytracker.sharing.domain.model.SleepPredictionSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
import com.google.firebase.Timestamp
import java.time.Instant

/** Read [key] as a Firestore list of maps and map each entry, defaulting to empty. */
private inline fun <T> Map<*, *>.mapList(key: String, mapper: (Map<*, *>) -> T): List<T> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map(mapper).orEmpty()

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
    "diapers" to snapshot.diapers.map { diaperToMap(it) },
    "doctorVisits" to snapshot.doctorVisits.map { doctorVisitToMap(it) },
)

internal fun diaperToMap(diaper: DiaperSnapshot): Map<String, Any?> = mapOf(
    "timestamp" to diaper.timestamp,
    "type" to diaper.type,
    "notes" to diaper.notes,
)

internal fun mapToDiaper(map: Map<*, *>): DiaperSnapshot = DiaperSnapshot(
    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
    type = map["type"] as? String ?: "WET",
    notes = map["notes"] as? String,
)

internal fun doctorVisitToMap(visit: DoctorVisitSnapshot): Map<String, Any?> = mapOf(
    "date" to visit.date,
    "providerName" to visit.providerName,
)

internal fun mapToDoctorVisit(map: Map<*, *>): DoctorVisitSnapshot = DoctorVisitSnapshot(
    date = (map["date"] as? Number)?.toLong() ?: 0L,
    providerName = map["providerName"] as? String,
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
    // Reasons ship as semantic maps and feedDue as a fact; each device resolves text in its own
    // locale. Older partner builds expected localized strings under "reasons"/"feedPrompt" — they
    // filter these maps out and simply show no reason line, which degrades gracefully.
    "reasons" to prediction.reasons.map { reasonToMap(it) },
    "feedDue" to prediction.feedDue,
    "generatedAt" to prediction.generatedAt,
)

internal fun reasonToMap(reason: SleepReason): Map<String, Any?> = when (reason) {
    is SleepReason.FullyPersonalized -> mapOf("type" to "FULLY_PERSONALIZED", "nextType" to reason.nextType.name)
    is SleepReason.Blended ->
        mapOf("type" to "BLENDED", "percent" to reason.percent, "nextType" to reason.nextType.name)
    is SleepReason.TypicalWakeWindow -> mapOf(
        "type" to "TYPICAL_WAKE_WINDOW",
        "ageInWeeks" to reason.ageInWeeks,
        "minMinutes" to reason.minMinutes,
        "maxMinutes" to reason.maxMinutes,
    )
    is SleepReason.TypeSpecificPattern -> mapOf(
        "type" to "TYPE_SPECIFIC_PATTERN",
        "nextType" to reason.nextType.name,
        "intervalCount" to reason.intervalCount,
    )
    is SleepReason.CombinedHistory -> mapOf("type" to "COMBINED_HISTORY", "nextType" to reason.nextType.name)
    SleepReason.Disruption -> mapOf("type" to "DISRUPTION")
    SleepReason.CircadianSlot -> mapOf("type" to "CIRCADIAN_SLOT")
    is SleepReason.NapDeficit -> mapOf("type" to "NAP_DEFICIT", "deficit" to reason.deficit)
    is SleepReason.SleepDebt -> mapOf("type" to "SLEEP_DEBT", "earlierWindow" to reason.earlierWindow)
}

private fun Map<*, *>.intField(key: String): Int = (this[key] as? Number)?.toInt() ?: 0

private fun Map<*, *>.longField(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L

private fun Map<*, *>.sleepTypeField(): SleepType = (this["nextType"] as? String)?.toSleepTypeSafe() ?: SleepType.NAP

/** Null for unknown reason types, so a partner on an older build drops reasons it can't render. */
internal fun mapToReason(map: Map<*, *>): SleepReason? =
    when (map["type"]) {
        "FULLY_PERSONALIZED" -> SleepReason.FullyPersonalized(map.sleepTypeField())
        "BLENDED" -> SleepReason.Blended(map.intField("percent"), map.sleepTypeField())
        "TYPICAL_WAKE_WINDOW" -> SleepReason.TypicalWakeWindow(
            ageInWeeks = map.intField("ageInWeeks"),
            minMinutes = map.longField("minMinutes"),
            maxMinutes = map.longField("maxMinutes"),
        )
        "TYPE_SPECIFIC_PATTERN" -> SleepReason.TypeSpecificPattern(map.sleepTypeField(), map.intField("intervalCount"))
        "COMBINED_HISTORY" -> SleepReason.CombinedHistory(map.sleepTypeField())
        "DISRUPTION" -> SleepReason.Disruption
        "CIRCADIAN_SLOT" -> SleepReason.CircadianSlot
        "NAP_DEFICIT" -> SleepReason.NapDeficit(map.intField("deficit"))
        "SLEEP_DEBT" -> SleepReason.SleepDebt(map["earlierWindow"] as? Boolean ?: false)
        else -> null
    }

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
    "pausedAtMs" to session.pausedAtMs,
)

internal fun sleepToMap(sleep: SleepSnapshot): Map<String, Any?> = mapOf(
    "id" to sleep.id,
    "startTime" to sleep.startTime,
    "endTime" to sleep.endTime,
    "sleepType" to sleep.sleepType,
    "notes" to sleep.notes,
    "clientId" to sleep.clientId,
    "startedBy" to sleep.startedBy,
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
    val sessions = data.mapList("sessions", ::mapToSession)
    val sleepRecords = data.mapList("sleepRecords", ::mapToSleep)
    val bottleFeeds = data.mapList("bottleFeeds", ::mapToBottleFeed)
    val milkBags = data.mapList("milkBags", ::mapToMilkBag)
    val sleepPrediction = (data["sleepPrediction"] as? Map<*, *>)?.let { mapToPrediction(it) }
    val growth = data.mapList("growth", ::mapToGrowth)
    val milestones = data.mapList("milestones", ::mapToMilestone)
    val diapers = data.mapList("diapers", ::mapToDiaper)
    val doctorVisits = data.mapList("doctorVisits", ::mapToDoctorVisit)
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
        diapers = diapers,
        doctorVisits = doctorVisits,
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
    reasons = map.mapList("reasons") { mapToReason(it) }.filterNotNull(),
    feedDue = map["feedDue"] as? Boolean ?: false,
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
    pausedAtMs = map["pausedAtMs"] as? Long,
)

internal fun mapToSleep(map: Map<*, *>): SleepSnapshot = SleepSnapshot(
    id = (map["id"] as? Long) ?: 0L,
    startTime = (map["startTime"] as? Long) ?: 0L,
    endTime = map["endTime"] as? Long,
    sleepType = map["sleepType"] as? String ?: "NAP",
    notes = map["notes"] as? String,
    clientId = map["clientId"] as? String ?: "",
    startedBy = map["startedBy"] as? String ?: "OWNER",
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

// Unknown action or missing required envelope fields -> null -> op skipped, never crash the listener.
// Wire casing is lowercase (matches firestore.rules); sleepType is uppercased back to SleepType.name.
internal fun mapToSleepOp(opId: String, map: Map<*, *>): SleepOp? {
    val action = (map["action"] as? String)?.let { raw ->
        SleepOpAction.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    } ?: return null
    return SleepOp(
        opId = opId,
        action = action,
        entryClientId = map["entryClientId"] as? String ?: return null,
        authorUid = map["authorUid"] as? String ?: return null,
        createdAtMs = (map["createdAtMs"] as? Number)?.toLong() ?: return null,
        startTimeMs = (map["startTimeMs"] as? Number)?.toLong(),
        endTimeMs = (map["endTimeMs"] as? Number)?.toLong(),
        sleepType = (map["sleepType"] as? String)?.uppercase(),
        notes = map["notes"] as? String,
    )
}

internal fun sleepOpToMap(op: SleepOp): Map<String, Any?> = buildMap {
    put("action", op.action.name.lowercase())
    put("entryClientId", op.entryClientId)
    put("authorUid", op.authorUid)
    put("createdAtMs", op.createdAtMs)
    op.startTimeMs?.let { put("startTimeMs", it) }
    op.endTimeMs?.let { put("endTimeMs", it) }
    op.sleepType?.let { put("sleepType", it.lowercase()) }
    op.notes?.let { put("notes", it) }
}
