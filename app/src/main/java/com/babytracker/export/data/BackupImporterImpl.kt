package com.babytracker.export.data

import androidx.room.withTransaction
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.BackupImporter
import com.babytracker.export.domain.ImportCounts
import com.babytracker.export.domain.model.BackupData
import com.babytracker.domain.model.toSleepTypeOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporterImpl @Inject constructor(
    private val db: BabyTrackerDatabase,
) : BackupImporter {

    override suspend fun merge(data: BackupData): ImportCounts = db.withTransaction {
        val bf = mergeBreastfeeding(data)
        val sleep = mergeSleep(data)
        val (pumpInserted, pumpIdMap) = mergePumping(data)
        val bags = mergeMilkBags(data, pumpIdMap)
        val bottles = mergeBottleFeeds(data)
        ImportCounts(bf, sleep, pumpInserted, bags, bottles)
    }

    private suspend fun mergeBreastfeeding(data: BackupData): Int {
        val seen = db.breastfeedingDao().getAllSessionsOnce().map { it.identity() }.toMutableSet()
        var inserted = 0
        for (b in data.breastfeeding) {
            val entity = BreastfeedingEntity(
                startTime = b.startTime, endTime = b.endTime, startingSide = b.startingSide,
                switchTime = b.switchTime, notes = b.notes, pausedAt = b.pausedAt,
                pausedDurationMs = b.pausedDurationMs,
            )
            if (seen.add(entity.identity())) {
                db.breastfeedingDao().insertSession(entity)
                inserted++
            }
        }
        return inserted
    }

    private suspend fun mergeSleep(data: BackupData): Int {
        val existingByIdentity = db.sleepDao().getAllRecordsOnce()
            .associateBy { it.identity() }
            .toMutableMap()
        var inserted = 0
        for (s in data.sleep) {
            val entity = SleepEntity(
                startTime = s.startTime,
                endTime = s.endTime,
                sleepType = s.sleepType.toSleepTypeOrNull()?.name ?: s.sleepType,
                notes = s.notes,
                timezoneId = s.timezoneId,
            )
            val key = entity.identity()
            val existing = existingByIdentity[key]
            when {
                existing == null -> {
                    val newId = db.sleepDao().insertRecord(entity)
                    existingByIdentity[key] = entity.copy(id = newId)
                    inserted++
                }
                existing.timezoneId == null && entity.timezoneId != null -> {
                    val repaired = existing.copy(timezoneId = entity.timezoneId)
                    db.sleepDao().updateRecord(repaired)
                    existingByIdentity[key] = repaired
                }
            }
        }
        return inserted
    }

    /** Returns (insertedCount, backupPumpingId -> dbId) for EVERY backup pumping id. */
    private suspend fun mergePumping(data: BackupData): Pair<Int, Map<Long, Long>> {
        val existingByIdentity = db.pumpingDao().getAllSessionsOnce()
            .associate { it.identity() to it.id }
            .toMutableMap()
        val idMap = HashMap<Long, Long>()
        var inserted = 0
        for (p in data.pumping) {
            val entity = PumpingEntity(
                startTime = p.startTime, endTime = p.endTime, breast = p.breast,
                volumeMl = p.volumeMl, notes = p.notes, pausedAt = p.pausedAt,
                pausedDurationMs = p.pausedDurationMs,
            )
            val key = entity.identity()
            val dbId = existingByIdentity[key] ?: run {
                val newId = db.pumpingDao().insert(entity)
                existingByIdentity[key] = newId
                inserted++
                newId
            }
            idMap[p.id] = dbId
        }
        return inserted to idMap
    }

    private suspend fun mergeMilkBags(data: BackupData, pumpIdMap: Map<Long, Long>): Int {
        val existingByIdentity =
            db.milkBagDao().getAllBagsOnce().associateBy { it.identity() }.toMutableMap()
        var inserted = 0
        for (m in data.milkBags) {
            val remappedSource = m.sourceSessionId?.let { pumpIdMap[it] }
            val entity = MilkBagEntity(
                collectionDate = m.collectionDate, volumeMl = m.volumeMl,
                sourceSessionId = remappedSource, usedAt = m.usedAt, notes = m.notes,
                createdAt = m.createdAt,
            )
            val key = entity.identity()
            val existing = existingByIdentity[key]
            when {
                existing == null -> {
                    db.milkBagDao().insert(entity)
                    existingByIdentity[key] = entity
                    inserted++
                }
                existing.sourceSessionId == null && remappedSource != null -> {
                    val repaired = existing.copy(sourceSessionId = remappedSource)
                    db.milkBagDao().update(repaired)
                    existingByIdentity[key] = repaired
                }
            }
        }
        return inserted
    }

    /**
     * Inserts backup bottle feeds, skipping exact duplicates. A feed's [linkedMilkBagId] is a FK
     * to a milk bag, whose db id is reassigned on import, so we resolve it the same way milk bags
     * resolve their pumping source: backup bag id -> backup bag identity -> real db bag id. Runs
     * after [mergeMilkBags] so the referenced bags already exist. linkedMilkBagId is excluded from
     * identity so a feed differing only in its (reassigned) link is treated as the same feed.
     */
    private suspend fun mergeBottleFeeds(data: BackupData): Int {
        val seen = db.bottleFeedDao().getAllOnce().map { it.identity() }.toMutableSet()
        val bagBackupById = data.milkBags.associateBy { it.id }
        val dbBagIdByIdentity =
            db.milkBagDao().getAllBagsOnce().associate { it.identity() to it.id }
        var inserted = 0
        for (f in data.bottleFeeds) {
            val resolvedBagId = f.linkedMilkBagId
                ?.let { bagBackupById[it] }
                ?.let { dbBagIdByIdentity[it.toEntity().identity()] }
            val entity = BottleFeedEntity(
                clientId = UUID.randomUUID().toString(), timestamp = f.timestamp,
                volumeMl = f.volumeMl, type = f.type, linkedMilkBagId = resolvedBagId,
                notes = f.notes, createdAt = f.createdAt,
            )
            if (seen.add(entity.identity())) {
                db.bottleFeedDao().insert(entity)
                inserted++
            }
        }
        return inserted
    }

    // Identity keys: every persisted field except autogen id; sourceSessionId also excluded for
    // milk bags so a bag differing only in link source is treated as the same inventory item.
    private fun BreastfeedingEntity.identity() =
        listOf(startTime, endTime, startingSide, switchTime, notes, pausedAt, pausedDurationMs)
    private fun SleepEntity.identity() = listOf(startTime, endTime, sleepType, notes)
    private fun PumpingEntity.identity() =
        listOf(startTime, endTime, breast, volumeMl, notes, pausedAt, pausedDurationMs)
    private fun MilkBagEntity.identity() = listOf(collectionDate, volumeMl, usedAt, notes, createdAt)
    private fun BottleFeedEntity.identity() = listOf(timestamp, volumeMl, type, notes, createdAt)
}
