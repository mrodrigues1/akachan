package com.babytracker.debug

import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.DiaperDao
import com.babytracker.data.local.dao.GrowthMeasurementDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.DiaperEntity
import com.babytracker.data.local.entity.GrowthMeasurementEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DebugDataSeeder @Inject constructor(
    private val babyRepository: BabyRepository,
    private val breastfeedingDao: BreastfeedingDao,
    private val sleepDao: SleepDao,
    private val pumpingDao: PumpingDao,
    private val milkBagDao: MilkBagDao,
    private val bottleFeedDao: BottleFeedDao,
    private val growthMeasurementDao: GrowthMeasurementDao,
    private val diaperDao: DiaperDao,
    private val settingsRepository: SettingsRepository,
) {
    private val zone = ZoneId.systemDefault()
    private val today: LocalDate get() = LocalDate.now()
    private val rng = Random(42)

    suspend fun seedIfEmpty() {
        // Runs every debug launch (not gated by the empty check) so an already-seeded install still
        // gets the partner flip; persisted, so it routes to the partner dashboard on the next launch.
        enterPartnerModeIfConfigured()
        if (babyRepository.isOnboardingComplete().first()) return
        // 5 months old so 4 months of history is plausible
        babyRepository.saveBabyProfile(Baby(name = "Sofia", birthDate = today.minusDays(150)))
        seedBreastfeedingSessions()
        seedSleepRecords()
        val pumpingIds = seedPumpingSessions()
        val activeBagIds = seedMilkBags(pumpingIds)
        seedBottleFeeds(activeBagIds)
        seedGrowthMeasurements()
        seedDiaperChanges()
    }

    /**
     * Flips local app mode to PARTNER with a placeholder share code — no Firebase connect — so the
     * partner UI/routing can be exercised offline (the dashboard stays empty until a real primary
     * publishes under this code). The start destination is resolved once per launch, so this takes
     * effect on the NEXT app launch after it's written. Set [ENTER_PARTNER_MODE] false to seed as a
     * normal primary instead.
     */
    private suspend fun enterPartnerModeIfConfigured() {
        if (!DebugSeedConfig.ENTER_PARTNER_MODE) return
        settingsRepository.setShareCode(DebugSeedConfig.PARTNER_SHARE_CODE)
        settingsRepository.setAppMode(AppMode.PARTNER)
    }

    private fun ms(daysAgo: Int, hour: Int, minute: Int): Long =
        today.minusDays(daysAgo.toLong())
            .atTime(LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun jitter(minutes: Int): Long =
        rng.nextLong(-minutes * 60_000L, minutes * 60_000L)

    // ── Breastfeeding ──────────────────────────────────────────────────────────

    private suspend fun seedBreastfeedingSessions() {
        val baseHours = listOf(6, 9, 12, 15, 18, 21, 0, 3)
        val sides = listOf("LEFT", "RIGHT")
        val nowMs = Instant.now().toEpochMilli()
        var sideIdx = 0
        for (daysAgo in 120 downTo 1) {
            for (hour in baseHours) {
                val dayCursor = if (hour < 6) daysAgo - 1 else daysAgo
                val startMs = ms(dayCursor, hour, 0) + jitter(20)
                val durationMs = (12L + rng.nextInt(14)) * 60_000L
                val endMs = startMs + durationMs
                // Consume rng unconditionally so seed sequence stays deterministic
                val switchMs: Long? = if (rng.nextFloat() < 0.4f) startMs + durationMs / 2 + jitter(5) else null
                if (endMs <= nowMs) {
                    breastfeedingDao.insertSession(
                        BreastfeedingEntity(
                            startTime = startMs,
                            endTime = endMs,
                            startingSide = sides[sideIdx % 2],
                            switchTime = switchMs,
                        )
                    )
                }
                sideIdx++
            }
        }
    }

    // ── Sleep ──────────────────────────────────────────────────────────────────

    /**
     * Three phases match the baby's developmental age.
     *
     * Phase 1 (daysAgo 120..61, ~4-13 weeks): 4 chaotic naps, broad jitter.
     * Phase 2 (daysAgo 60..15, ~13-19 weeks): 3 naps, moderate consistency.
     * Phase 3 (daysAgo 14..0, ~19-21 weeks): 3 naps, tight wake windows
     *   designed to satisfy the sleep-prediction quality gate:
     *   IQR([120, 135, 150, 120] min) ≈ 22 min — well under the 45-min ceiling.
     *
     * Today's records (daysAgo=0) are gated by nowMs so only past times insert.
     */
    private suspend fun seedSleepRecords() {
        val nowMs = Instant.now().toEpochMilli()
        for (daysAgo in 120 downTo 0) {
            seedDaySleep(daysAgo, nowMs)
        }
    }

    private suspend fun seedDaySleep(daysAgo: Int, nowMs: Long) {
        val cfg = sleepPhaseConfig(daysAgo)
        val nightStartMs = ms(daysAgo + 1, cfg.nightStartHour, cfg.nightStartMin) + jitter(cfg.nightJitter)
        val nightEndMs = ms(daysAgo, 7, 0) + jitter(cfg.nightJitter)
        if (nightStartMs < nightEndMs && nightEndMs <= nowMs) {
            sleepDao.insertRecord(SleepEntity(startTime = nightStartMs, endTime = nightEndMs, sleepType = "NIGHT_SLEEP"))
        }
        for ((hour, minute, minDur, maxDur) in cfg.napDefs) {
            val startMs = ms(daysAgo, hour, minute) + jitter(cfg.napJitter)
            val durationMs = (minDur.toLong() + rng.nextInt(maxDur - minDur + 1)) * 60_000L
            val endMs = startMs + durationMs
            if (startMs < endMs && endMs <= nowMs) {
                sleepDao.insertRecord(SleepEntity(startTime = startMs, endTime = endMs, sleepType = "NAP"))
            }
        }
    }

    private data class NapDef(val hour: Int, val minute: Int, val minDur: Int, val maxDur: Int)

    private data class SleepPhaseConfig(
        val nightJitter: Int,
        val napJitter: Int,
        val nightStartHour: Int,
        val nightStartMin: Int,
        val napDefs: List<NapDef>,
    )

    // Phase 3 uses ±5 min jitter (not ±8/±10) to keep wake-interval IQR ≤ 45 min.
    // Wider jitter + 10-min duration range compounds to ±21 min per interval,
    // which can push the 53-sample IQR above the 45-min quality gate.
    private fun sleepPhaseConfig(daysAgo: Int): SleepPhaseConfig = when {
        daysAgo > 60 -> SleepPhaseConfig(
            nightJitter = 30, napJitter = 20,
            nightStartHour = 20, nightStartMin = 0,
            napDefs = listOf(
                NapDef(9, 0, 30, 60),
                NapDef(11, 30, 30, 60),
                NapDef(14, 0, 20, 45),
                NapDef(16, 0, 15, 30),
            ),
        )
        daysAgo > 14 -> SleepPhaseConfig(
            nightJitter = 20, napJitter = 15,
            nightStartHour = 20, nightStartMin = 0,
            napDefs = listOf(
                NapDef(9, 0, 45, 75),
                NapDef(12, 0, 60, 90),
                NapDef(15, 30, 30, 50),
            ),
        )
        else -> SleepPhaseConfig(
            nightJitter = 5, napJitter = 5,
            nightStartHour = 18, nightStartMin = 45,
            // WW pattern 120 / 135 / 150 / 120 min with ±5 min jitter and ±2 min
            // duration variance → each interval range ≤ 24 min → max IQR ≈ 39 min ✓
            napDefs = listOf(
                NapDef(9, 0, 58, 62),
                NapDef(12, 15, 73, 77),
                NapDef(16, 0, 43, 47),
            ),
        )
    }

    // ── Pumping ────────────────────────────────────────────────────────────────

    private suspend fun seedPumpingSessions(): List<Long> {
        val breastOptions = listOf("LEFT", "RIGHT", "BOTH")
        val ids = mutableListOf<Long>()
        for (daysAgo in 120 downTo 1) {
            val morningStart = ms(daysAgo, 7, 30) + jitter(20)
            ids += pumpingDao.insert(
                PumpingEntity(
                    startTime = morningStart,
                    endTime = morningStart + (15L + rng.nextInt(11)) * 60_000L,
                    breast = breastOptions[rng.nextInt(breastOptions.size)],
                    volumeMl = 70 + rng.nextInt(61),
                )
            )
            val afternoonStart = ms(daysAgo, 14, 0) + jitter(20)
            ids += pumpingDao.insert(
                PumpingEntity(
                    startTime = afternoonStart,
                    endTime = afternoonStart + (12L + rng.nextInt(10)) * 60_000L,
                    breast = breastOptions[rng.nextInt(breastOptions.size)],
                    volumeMl = 60 + rng.nextInt(51),
                )
            )
            if (rng.nextFloat() < 0.5f) {
                val eveningStart = ms(daysAgo, 20, 0) + jitter(15)
                ids += pumpingDao.insert(
                    PumpingEntity(
                        startTime = eveningStart,
                        endTime = eveningStart + (10L + rng.nextInt(11)) * 60_000L,
                        breast = breastOptions[rng.nextInt(breastOptions.size)],
                        volumeMl = 50 + rng.nextInt(41),
                    )
                )
            }
        }
        return ids
    }

    // ── Milk bags ──────────────────────────────────────────────────────────────

    /**
     * Builds the freezer stash. Far fewer bags than before (~0.7/day, not 1–3/day): most pumped
     * milk gets fed back through [seedBottleFeeds], so only the surplus accumulates. Returns the
     * inserted bag ids oldest-first so bottle feeds can consume from the front of the stash; the
     * leftovers are the "current stash" the inventory screen shows.
     */
    private suspend fun seedMilkBags(pumpingIds: List<Long>): MutableList<Long> {
        val activeBagIds = mutableListOf<Long>()
        var sessionIdx = 0
        for (daysAgo in 120 downTo 1) {
            if (rng.nextFloat() >= 0.7f) continue // skip ~30% of days
            val collectionMs = ms(daysAgo, 8, 0) + jitter(120)
            activeBagIds += milkBagDao.insert(
                MilkBagEntity(
                    collectionDate = collectionMs,
                    volumeMl = 75 + rng.nextInt(76),
                    sourceSessionId = pumpingIds.getOrNull(sessionIdx++),
                    createdAt = collectionMs,
                )
            )
        }
        return activeBagIds
    }

    // ── Bottle feeds ─────────────────────────────────────────────────────────────

    /**
     * One bottle a day, an evening top-up alongside the breastfeeding sessions. About half are
     * expressed breast milk drawn from [activeBagIds] (consuming a bag from the stash via
     * [BottleFeedDao.insertWithBagConsume]); the rest are formula. Mutates [activeBagIds],
     * popping each consumed bag so the remainder reflects the true stash.
     */
    private suspend fun seedBottleFeeds(activeBagIds: MutableList<Long>) {
        val nowMs = Instant.now().toEpochMilli()
        for (daysAgo in 120 downTo 1) {
            val ts = ms(daysAgo, 19, 0) + jitter(45)
            if (ts > nowMs) continue
            val useBreastMilk = rng.nextFloat() < 0.5f && activeBagIds.isNotEmpty()
            val bagId = if (useBreastMilk) activeBagIds.removeAt(0) else null
            val type = if (useBreastMilk) FeedType.BREAST_MILK else FeedType.FORMULA
            bottleFeedDao.insertWithBagConsume(
                entity = BottleFeedEntity(
                    clientId = UUID.randomUUID().toString(),
                    timestamp = ts,
                    volumeMl = 90 + rng.nextInt(61), // 90–150 ml
                    type = type.name,
                    createdAt = ts,
                    author = FeedAuthor.OWNER.name,
                ),
                consumedBagId = bagId,
                usedAt = ts,
            )
        }
    }

    // ── Growth ─────────────────────────────────────────────────────────────────

    private data class GrowthPoint(val daysAgo: Int, val weightG: Int, val lengthMm: Int, val headMm: Int)

    /**
     * Monthly checkpoints from birth (daysAgo 150) to ~5 months, tracking roughly the WHO 50th
     * percentile for a girl. Canonical units: grams for weight, millimetres for length/head circ.
     */
    private suspend fun seedGrowthMeasurements() {
        val checkpoints = listOf(
            GrowthPoint(150, 3300, 500, 345), // birth
            GrowthPoint(120, 4200, 540, 370), // ~1 mo
            GrowthPoint(90, 5100, 575, 388), // ~2 mo
            GrowthPoint(60, 5800, 600, 400), // ~3 mo
            GrowthPoint(30, 6400, 625, 412), // ~4 mo
            GrowthPoint(5, 6900, 645, 420), // ~5 mo
        )
        for (point in checkpoints) {
            val takenAt = ms(point.daysAgo, 10, 0)
            growthMeasurementDao.insert(
                GrowthMeasurementEntity(takenAt = takenAt, type = GrowthType.WEIGHT.name, valueCanonical = point.weightG.toLong()),
            )
            growthMeasurementDao.insert(
                GrowthMeasurementEntity(takenAt = takenAt, type = GrowthType.LENGTH.name, valueCanonical = point.lengthMm.toLong()),
            )
            growthMeasurementDao.insert(
                GrowthMeasurementEntity(takenAt = takenAt, type = GrowthType.HEAD_CIRC.name, valueCanonical = point.headMm.toLong()),
            )
        }
    }

    // ── Diapers ────────────────────────────────────────────────────────────────

    /**
     * ~6 changes a day across the waking hours, typical for a 5-month-old: mostly wet, a couple
     * dirty, the odd both. Includes today up to [now][Instant.now] so the home tile has data.
     */
    private suspend fun seedDiaperChanges() {
        val nowMs = Instant.now().toEpochMilli()
        val baseHours = listOf(7, 10, 13, 16, 19, 22)
        for (daysAgo in 120 downTo 0) {
            for (hour in baseHours) {
                val ts = ms(daysAgo, hour, 0) + jitter(30)
                if (ts > nowMs) continue
                val type = when {
                    rng.nextFloat() < 0.25f -> DiaperType.DIRTY
                    rng.nextFloat() < 0.13f -> DiaperType.BOTH
                    else -> DiaperType.WET
                }
                diaperDao.insert(DiaperEntity(timestamp = ts, type = type.name, createdAt = ts))
            }
        }
    }
}
