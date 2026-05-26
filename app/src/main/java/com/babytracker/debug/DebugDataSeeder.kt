package com.babytracker.debug

import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugDataSeeder @Inject constructor(
    private val babyRepository: BabyRepository,
    private val breastfeedingDao: BreastfeedingDao,
    private val sleepDao: SleepDao,
    private val pumpingDao: PumpingDao,
    private val milkBagDao: MilkBagDao,
) {
    private val zone = ZoneId.systemDefault()
    private val today: LocalDate get() = LocalDate.now()

    suspend fun seedIfEmpty() {
        if (babyRepository.isOnboardingComplete().first()) return
        babyRepository.saveBabyProfile(Baby(name = "Sofia", birthDate = today.minusDays(90)))
        seedBreastfeedingSessions()
        seedSleepRecords()
        val pumpingIds = seedPumpingSessions()
        seedMilkBags(pumpingIds)
    }

    private fun ms(daysAgo: Int, hour: Int, minute: Int): Long =
        today.minusDays(daysAgo.toLong())
            .atTime(LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private suspend fun seedBreastfeedingSessions() {
        val feedHours = listOf(6, 9, 12, 15, 18, 21, 0, 3)
        val sides = listOf("LEFT", "RIGHT")
        var idx = 0
        for (daysAgo in 7 downTo 1) {
            for ((feedIdx, hour) in feedHours.withIndex()) {
                val dayCursor = if (hour < 6) daysAgo - 1 else daysAgo
                val startMs = ms(dayCursor, hour, 0)
                val durationMs = (10 + idx % 8) * 60_000L
                val switchMs: Long? = if (feedIdx % 3 == 0) startMs + durationMs / 2 else null
                breastfeedingDao.insertSession(
                    BreastfeedingEntity(
                        startTime = startMs,
                        endTime = startMs + durationMs,
                        startingSide = sides[idx % 2],
                        switchTime = switchMs,
                    )
                )
                idx++
            }
        }
    }

    private suspend fun seedSleepRecords() {
        for (daysAgo in 7 downTo 1) {
            sleepDao.insertRecord(
                SleepEntity(
                    startTime = ms(daysAgo + 1, 22, 0),
                    endTime = ms(daysAgo, 6, 0),
                    sleepType = "NIGHT_SLEEP",
                )
            )
            sleepDao.insertRecord(
                SleepEntity(
                    startTime = ms(daysAgo, 9, 0),
                    endTime = ms(daysAgo, 9, 45),
                    sleepType = "NAP",
                )
            )
            sleepDao.insertRecord(
                SleepEntity(
                    startTime = ms(daysAgo, 13, 0),
                    endTime = ms(daysAgo, 13, 40),
                    sleepType = "NAP",
                )
            )
        }
    }

    private suspend fun seedPumpingSessions(): List<Long> {
        val breasts = listOf("LEFT", "RIGHT", "BOTH", "LEFT", "RIGHT")
        val ids = mutableListOf<Long>()
        for (daysAgo in 5 downTo 1) {
            val i = 5 - daysAgo
            val volume = 80 + i * 10
            val morningStart = ms(daysAgo, 8, 0)
            ids += pumpingDao.insert(
                PumpingEntity(
                    startTime = morningStart,
                    endTime = morningStart + 18 * 60_000L,
                    breast = breasts[i],
                    volumeMl = volume,
                )
            )
            val afternoonStart = ms(daysAgo, 14, 0)
            pumpingDao.insert(
                PumpingEntity(
                    startTime = afternoonStart,
                    endTime = afternoonStart + 15 * 60_000L,
                    breast = breasts[(i + 1) % breasts.size],
                    volumeMl = volume - 10,
                )
            )
        }
        return ids
    }

    private suspend fun seedMilkBags(pumpingIds: List<Long>) {
        val volumes = listOf(100, 90, 110)
        for (i in 0 until 3) {
            val collectionMs = ms(5 - i, 8, 20)
            milkBagDao.insert(
                MilkBagEntity(
                    collectionDate = collectionMs,
                    volumeMl = volumes[i],
                    sourceSessionId = pumpingIds.getOrNull(i),
                    createdAt = collectionMs,
                )
            )
        }
    }
}
